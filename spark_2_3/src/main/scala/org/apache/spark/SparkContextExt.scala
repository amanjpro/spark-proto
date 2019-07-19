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
package org.apache.spark

import scala.reflect.ClassTag
import org.apache.hadoop.fs.FileSystem
import com.google.protobuf.Message

import org.apache.hadoop.conf.Configuration
import org.apache.hadoop.io.NullWritable
import org.apache.hadoop.mapred.JobConf
import org.apache.hadoop.mapreduce.Job
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat

import org.apache.spark.rdd.{ProtobufHadoopRDD, RDD}
import org.apache.spark.deploy.SparkHadoopUtil

// This is an ugnly hack to make to add `newProtobufRDD` and `newAPIProtobufFile`
// functions that are almost exactly like `newHadoopRDD` and `newAPIHadoopFile`,
// except they accept a constructor to construct InputFile instances, instead of
// constructing them via reflection.
// This is handy to allow the library be used inside `spark-shell` and also, to
// be able to pass `parseDelimitedFrom` to the reader, instead of creating a whole
// new `FileInputFormat` class per protobuf message type.
trait SparkContextExt {
  val sc: SparkContext

  /**
   * Get an RDD for a given Hadoop file with an arbitrary new API FileInputFormat
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
  def newProtobufRDD[K <: Message : ClassTag, F <: FileInputFormat[K, NullWritable] : ClassTag](
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
      F <: FileInputFormat[K, NullWritable] : ClassTag](
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
