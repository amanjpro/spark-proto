/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
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

import java.io.{FileNotFoundException, IOException}
import java.text.SimpleDateFormat
import java.util.{Date, Locale}

import scala.collection.JavaConverters.asScalaBufferConverter
import scala.reflect.ClassTag
import org.apache.hadoop.fs.FileSystem

import com.google.protobuf.Message

import org.apache.hadoop.conf.{Configurable, Configuration}
import org.apache.hadoop.io.Writable
import org.apache.hadoop.io.NullWritable
import org.apache.hadoop.mapred.JobConf
import org.apache.hadoop.mapreduce._
import org.apache.hadoop.mapreduce.lib.input.{CombineFileSplit, FileInputFormat, FileSplit, InvalidInputException}
import org.apache.hadoop.mapreduce.task.{JobContextImpl, TaskAttemptContextImpl}

import org.apache.spark._
import org.apache.spark.annotation.DeveloperApi
import org.apache.spark.deploy.SparkHadoopUtil
import org.apache.spark.internal.Logging
import org.apache.spark.internal.config._
import org.apache.spark.rdd.NewHadoopRDD.NewHadoopMapPartitionsWithSplitRDD
import org.apache.spark.storage.StorageLevel
import org.apache.spark.util.{SerializableConfiguration, ShutdownHookManager}

import me.amanj.spark.proto.reader.{ProtobufInputFormat}
/**
 * :: DeveloperApi ::
 * An RDD that provides core functionality for reading data stored in Hadoop (e.g., files in HDFS,
 * sources in HBase, or S3), using the new MapReduce API (`org.apache.hadoop.mapreduce`).
 *
 * @param sc The SparkContext to associate the RDD with.
 * @param inputFormatClass Storage format of the data to be read.
 * @param keyClass Class of the key associated with the inputFormatClass.
 * @param valueClass Class of the value associated with the inputFormatClass.
 *
 * @note Instantiating this class directly is not recommended, please use
 * `org.apache.spark.SparkContext.newAPIHadoopRDD()`
 */
class ProtobufHadoopRDD[K <: Message, F <: ProtobufInputFormat[K]](
    sc : SparkContext,
    inputFormatConstructor: () => F,
    keyClass: Class[K],
    @transient private val _conf: Configuration)(implicit ftag: ClassTag[F])
      extends NewHadoopRDD[K, NullWritable](sc,
        ftag.runtimeClass.asInstanceOf[Class[F]], keyClass, classOf[NullWritable], _conf) {


  private val ignoreCorruptFiles = sparkContext.conf.get(IGNORE_CORRUPT_FILES)
  private val jobTrackerId: String = jobId.getJtIdentifier

  override def getPartitions: Array[Partition] = {
    val inputFormat = inputFormatConstructor()
    inputFormat match {
      case configurable: Configurable =>
        configurable.setConf(_conf)
      case _ =>
    }
    val jobContext = new JobContextImpl(_conf, jobId)
    val rawSplits = inputFormat.getSplits(jobContext).toArray
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

trait SparkContextExt {
  val sc: SparkContext

  // def protobuf[K <: Message : ClassTag, F <: ProtobufInputFormat[K]: ClassTag : TypeTag](path: String): RDD[K] = {
  //   new Protobuf[K](sc).read(path)
  // }

  /**
   * Get an RDD for a given Hadoop file with an arbitrary new API InputFormat
   * and extra configuration options to pass to the input format.
   *
   * @param conf Configuration for setting up the dataset. Note: This will be put into a Broadcast.
   *             Therefore if you plan to reuse this conf to create multiple RDDs, you need to make
   *             sure you won't modify the conf. A safe approach is always creating a new conf for
   *             a new RDD.
   * @param fClass storage format of the data to be read
   * @param kClass `Class` of the key associated with the `fClass` parameter
   * @param vClass `Class` of the value associated with the `fClass` parameter
   *
   * @note Because Hadoop's RecordReader class re-uses the same Writable object for each
   * record, directly caching the returned RDD or directly passing it to an aggregation or shuffle
   * operation will create many references to the same object.
   * If you plan to directly cache, sort, or aggregate Hadoop writable objects, you should first
   * copy them using a `map` function.
   */
  def newProtobufRDD[K <: Message : ClassTag, F <: ProtobufInputFormat[K] : ClassTag](
      inputFormatConstructor: () => F,
      conf: Configuration = sc.hadoopConfiguration): ProtobufHadoopRDD[K, F] = sc.withScope {
    sc.assertNotStopped()

    // This is a hack to enforce loading hdfs-site.xml.
    // See SPAK-11227 for details.
    FileSystem.getLocal(conf)

    // Add necessary security credentials to the JobConf. Required to access secure HDFS.
    val jconf = new JobConf(conf)
    SparkHadoopUtil.get.addCredentials(jconf)
    new ProtobufHadoopRDD(sc, inputFormatConstructor, implicitly[ClassTag[K]].runtimeClass.asInstanceOf[Class[K]], jconf)
  }

  /**
   * Smarter version of `newApiHadoopFile` that uses class tags to figure out the classes of keys,
   * values and the `org.apache.hadoop.mapreduce.InputFormat` (new MapReduce API) so that user
   * don't need to pass them directly. Instead, callers can just write, for example:
   * ```
   * val file = sparkContext.hadoopFile[LongWritable, Text, TextInputFormat](path)
   * ```
   *
   * @note Because Hadoop's RecordReader class re-uses the same Writable object for each
   * record, directly caching the returned RDD or directly passing it to an aggregation or shuffle
   * operation will create many references to the same object.
   * If you plan to directly cache, sort, or aggregate Hadoop writable objects, you should first
   * copy them using a `map` function.
   * @param path directory to the input data files, the path can be comma separated paths
   * as a list of inputs
   * @return RDD of tuples of key and corresponding value
   */
  def newAPIProtobufFile[K <: Message : ClassTag,
      F <: ProtobufInputFormat[K] : ClassTag](
        path: String, inputFormatConstructor: () => F,
        conf: Configuration = sc.hadoopConfiguration): RDD[K] = sc.withScope {

    sc.assertNotStopped()

    // This is a hack to enforce loading hdfs-site.xml.
    // See SPARK-11227 for details.
    FileSystem.getLocal(sc.hadoopConfiguration)

    // The call to NewHadoopJob automatically adds security credentials to conf,
    // so we don't need to explicitly add them ourselves
    val job = Job.getInstance(conf)
    // Use setInputPaths so that newAPIHadoopFile aligns with hadoopFile/textFile in taking
    // comma separated files as input. (see SPARK-7155)
    FileInputFormat.setInputPaths(job, path)
    val updatedConf = job.getConfiguration
    newProtobufRDD[K, F](
      inputFormatConstructor,
      updatedConf).keys
  }
}
