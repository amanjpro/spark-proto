/*
 * Licensed to Amanj Sherwany <<http://amanj.m>>
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
package me.amanj.spark.proto.reader

import org.apache.hadoop.fs.Path
import org.apache.spark.{SparkContext, SparkContextExt}
import org.apache.spark.rdd.RDD
import com.google.protobuf.Message
import java.io.InputStream
import scala.reflect.ClassTag

class ProtobufRDDReader[K <: Message : ClassTag](sc: SparkContext, parser: InputStream => K) {
  self =>

  implicit class SparkContextExtImpl(val sc: SparkContext) extends SparkContextExt
  private[this] val inputFormatConstructor: () => ProtobufInputFormat[K] = {
    val localParser = parser
    () => new ProtobufInputFormat(localParser)
  }

  private[this] def readImpl(input: String): RDD[K] = {
    val hadoopConf = sc.hadoopConfiguration
    val hadoopPath = new Path(input)

    // I need to get the FileSystem that stupid way to handle both
    // S3 and HDFS file systems.
    // -- Amanj
    val outerParser = parser
    if (hadoopPath.getFileSystem(hadoopConf).exists(hadoopPath)) {
      sc.newAPIProtobufFile(input, inputFormatConstructor)
    } else {
      sc.emptyRDD[K]
    }
  }

  def read(input: String): RDD[K] =
    readImpl(input)
}
