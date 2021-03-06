/*
 * Licensed to Amanj Sherwany <<http://amanj.me>> and the Apache Software
 * Foundation (ASF) under one or more contributor license agreements.
 *
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.spark.rdd

import java.io.{IOException, EOFException}

import scala.reflect.ClassTag
import com.google.protobuf.Message

import org.apache.hadoop.conf.{Configurable, Configuration}
import org.apache.hadoop.io.Writable
import org.apache.hadoop.io.NullWritable
import org.apache.hadoop.mapreduce.{TaskAttemptID, InputSplit, TaskType}
import org.apache.hadoop.mapreduce.lib.input.{CombineFileSplit, FileInputFormat, FileSplit}
import org.apache.hadoop.mapreduce.task.{JobContextImpl, TaskAttemptContextImpl}

import org.apache.spark.{SparkContext, Partition, TaskContext, InterruptibleIterator}
import org.apache.spark.deploy.SparkHadoopUtil
import org.apache.spark.executor.DataReadMethod
import org.apache.spark.util.ShutdownHookManager

class ProtobufHadoopRDD[K <: Message, F <: FileInputFormat[K, NullWritable]](
    sc : SparkContext,
    inputFormatConstructor: () => F,
    keyClass: Class[K],
    @transient private val _conf: Configuration)(implicit ftag: ClassTag[F])
      extends NewHadoopRDD[K, NullWritable](sc,
        ftag.runtimeClass.asInstanceOf[Class[F]], keyClass, classOf[NullWritable], _conf) {


  private val ignoreCorruptFiles =
    sparkContext.conf.getBoolean("spark.files.ignoreCorruptFiles", true)

  private val jobTrackerId: String = jobId.getJtIdentifier

  override def getPartitions: Array[Partition] = {
    val inputFormat = inputFormatConstructor()
    inputFormat match {
      case configurable: Configurable =>
        configurable.setConf(_conf)
      case _ =>
    }
    val jobContext = newJobContext(_conf, jobId)
    val rawSplits = inputFormat.getSplits(jobContext).toArray
    val result = new Array[Partition](rawSplits.size)
    for (i <- 0 until rawSplits.size) {
      result(i) = new NewHadoopPartition(id, i, rawSplits(i).asInstanceOf[InputSplit with Writable])
    }
    result
  }

  override def compute(theSplit: Partition, context: TaskContext): InterruptibleIterator[(K, NullWritable)] = {
    val iter = new Iterator[(K, NullWritable)] {
      val split = theSplit.asInstanceOf[NewHadoopPartition]
      logInfo("Input split: " + split.serializableHadoopSplit)
      val conf = getConf

      val inputMetrics = context.taskMetrics
        .getInputMetricsForReadMethod(DataReadMethod.Hadoop)

      // Sets the thread local variable for the file's name
      split.serializableHadoopSplit.value match {
        case fs: FileSplit => SqlNewHadoopRDDState.setInputFileName(fs.getPath.toString)
        case _ => SqlNewHadoopRDDState.unsetInputFileName()
      }

      // Find a function that will return the FileSystem bytes read by this thread. Do this before
      // creating RecordReader, because RecordReader's constructor might read some bytes
      val bytesReadCallback = inputMetrics.bytesReadCallback.orElse {
        split.serializableHadoopSplit.value match {
          case _: FileSplit | _: CombineFileSplit =>
            SparkHadoopUtil.get.getFSBytesReadOnThreadCallback()
          case _ => None
        }
      }
      inputMetrics.setBytesReadCallback(bytesReadCallback)

      val format = inputFormatConstructor()
      format match {
        case configurable: Configurable =>
          configurable.setConf(conf)
        case _ =>
      }
      val attemptId = newTaskAttemptID(jobTrackerId, id, isMap = true, split.index, 0)
      val hadoopAttemptContext = newTaskAttemptContext(conf, attemptId)
      private var reader = format.createRecordReader(
        split.serializableHadoopSplit.value, hadoopAttemptContext)
      reader.initialize(split.serializableHadoopSplit.value, hadoopAttemptContext)

      // Register an on-task-completion callback to close the input stream.
      context.addTaskCompletionListener(context => close())
      var havePair = false
      var finished = false
      var recordsSinceMetricsUpdate = 0

      override def hasNext: Boolean = {
        if (!finished && !havePair) {
          try {
            finished = !reader.nextKeyValue
          } catch {
            case _: EOFException if ignoreCorruptFiles => finished = true
          }
          if (finished) {
            // Close and release the reader here; close() will also be called when the task
            // completes, but for tasks that read from many files, it helps to release the
            // resources early.
            close()
          }
          havePair = !finished
        }
        !finished
      }

      override def next(): (K, NullWritable) = {
        if (!hasNext) {
          throw new java.util.NoSuchElementException("End of stream")
        }
        havePair = false
        if (!finished) {
          inputMetrics.incRecordsRead(1)
        }
        (reader.getCurrentKey, reader.getCurrentValue)
      }

      private def close() {
        if (reader != null) {
          SqlNewHadoopRDDState.unsetInputFileName()
          // Close the reader and release it. Note: it's very important that we don't close the
          // reader more than once, since that exposes us to MAPREDUCE-5918 when running against
          // Hadoop 1.x and older Hadoop 2.x releases. That bug can lead to non-deterministic
          // corruption issues when reading compressed input.
          try {
            reader.close()
          } catch {
            case e: Exception =>
              if (!ShutdownHookManager.inShutdown()) {
                logWarning("Exception in RecordReader.close()", e)
              }
          } finally {
            reader = null
          }
          if (bytesReadCallback.isDefined) {
            inputMetrics.updateBytesRead()
          } else if (split.serializableHadoopSplit.value.isInstanceOf[FileSplit] ||
                     split.serializableHadoopSplit.value.isInstanceOf[CombineFileSplit]) {
            // If we can't get the bytes read from the FS stats, fall back to the split size,
            // which may be inaccurate.
            try {
              inputMetrics.incBytesRead(split.serializableHadoopSplit.value.getLength)
            } catch {
              case e: java.io.IOException =>
                logWarning("Unable to get input size to set InputMetrics for task", e)
            }
          }
        }
      }
    }
    new InterruptibleIterator(context, iter)
  }
}
