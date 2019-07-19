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

import java.io.IOException

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
import scala.collection.JavaConverters.asScalaBufferConverter
import org.apache.spark.internal.config._
import org.apache.spark.util.ShutdownHookManager

class ProtobufHadoopRDD[K <: Message, F <: FileInputFormat[K, NullWritable]](
    sc : SparkContext,
    inputFormatConstructor: () => F,
    keyClass: Class[K],
    @transient private val _conf: Configuration)(implicit ftag: ClassTag[F])
      extends NewHadoopRDD[K, NullWritable](sc,
        ftag.runtimeClass.asInstanceOf[Class[F]], keyClass, classOf[NullWritable], _conf) {


  private val ignoreCorruptFiles = sparkContext.conf.get(IGNORE_CORRUPT_FILES)
  private val ignoreEmptySplits = sparkContext.conf.get(HADOOP_RDD_IGNORE_EMPTY_SPLITS)

  private val jobTrackerId: String = jobId.getJtIdentifier

  override def getPartitions: Array[Partition] = {
    val inputFormat = inputFormatConstructor()
    inputFormat match {
      case configurable: Configurable =>
        configurable.setConf(_conf)
      case _ =>
    }
    val allRowSplits = inputFormat.getSplits(new JobContextImpl(_conf, jobId)).asScala
    val rawSplits = if (ignoreEmptySplits) {
      allRowSplits.filter(_.getLength > 0)
    } else {
      allRowSplits
    }
    val result = new Array[Partition](rawSplits.size)

    for (i <- 0 until rawSplits.size) {
      result(i) = new NewHadoopPartition(id, i, rawSplits(i).asInstanceOf[InputSplit with Writable])
    }
    result
  }

  override def compute(theSplit: Partition, context: TaskContext): InterruptibleIterator[(K, NullWritable)] = {
    val iter = new Iterator[(K, NullWritable)] {
      private val split = theSplit.asInstanceOf[NewHadoopPartition]
      logInfo("Input split: " + split.serializableHadoopSplit)
      private val conf = getConf

      private val inputMetrics = context.taskMetrics().inputMetrics
      private val existingBytesRead = inputMetrics.bytesRead

      // Sets InputFileBlockHolder for the file block's information
      split.serializableHadoopSplit.value match {
        case fs: FileSplit =>
          InputFileBlockHolder.set(fs.getPath.toString, fs.getStart, fs.getLength)
        case _ =>
          InputFileBlockHolder.unset()
      }

      // Find a function that will return the FileSystem bytes read by this thread. Do this before
      // creating RecordReader, because RecordReader's constructor might read some bytes
      private val getBytesReadCallback: Option[() => Long] =
        split.serializableHadoopSplit.value match {
          case _: FileSplit | _: CombineFileSplit =>
            Some(SparkHadoopUtil.get.getFSBytesReadOnThreadCallback())
          case _ => None
        }

      // We get our input bytes from thread-local Hadoop FileSystem statistics.
      // If we do a coalesce, however, we are likely to compute multiple partitions in the same
      // task and in the same thread, in which case we need to avoid override values written by
      // previous partitions (SPARK-13071).
      private def updateBytesRead(): Unit = {
        getBytesReadCallback.foreach { getBytesRead =>
          inputMetrics.setBytesRead(existingBytesRead + getBytesRead())
        }
      }

      private val format = inputFormatConstructor()
      format match {
        case configurable: Configurable =>
          configurable.setConf(conf)
        case _ =>
      }
      private val attemptId = new TaskAttemptID(jobTrackerId, id, TaskType.MAP, split.index, 0)
      private val hadoopAttemptContext = new TaskAttemptContextImpl(conf, attemptId)
      private var finished = false
      private var reader =
        try {
          val _reader = format.createRecordReader(
            split.serializableHadoopSplit.value, hadoopAttemptContext)
          _reader.initialize(split.serializableHadoopSplit.value, hadoopAttemptContext)
          _reader
        } catch {
          case e: IOException if ignoreCorruptFiles =>
            logWarning(
              s"Skipped the rest content in the corrupted file: ${split.serializableHadoopSplit}",
              e)
            finished = true
            null
        }

      // Register an on-task-completion callback to close the input stream.
      context.addTaskCompletionListener { context =>
        // Update the bytesRead before closing is to make sure lingering bytesRead statistics in
        // this thread get correctly added.
        updateBytesRead()
        close()
      }

      private var havePair = false
      private var recordsSinceMetricsUpdate = 0

      override def hasNext: Boolean = {
        if (!finished && !havePair) {
          try {
            finished = !reader.nextKeyValue
          } catch {
            case e: IOException if ignoreCorruptFiles =>
              logWarning(
                s"Skipped the rest content in the corrupted file: ${split.serializableHadoopSplit}",
                e)
              finished = true
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
        if (inputMetrics.recordsRead % SparkHadoopUtil.UPDATE_INPUT_METRICS_INTERVAL_RECORDS == 0) {
          updateBytesRead()
        }
        (reader.getCurrentKey, reader.getCurrentValue)
      }

      private def close(): Unit = {
        if (reader != null) {
          InputFileBlockHolder.unset()
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
          if (getBytesReadCallback.isDefined) {
            updateBytesRead()
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
