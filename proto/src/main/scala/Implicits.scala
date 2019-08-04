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
package me.amanj.spark.proto

import me.amanj.spark.proto.reader.ProtobufRDDReader
import me.amanj.spark.proto.writer.ProtobufRDDWriter
import com.google.protobuf.Message
import org.apache.spark.SparkContext
import org.apache.spark.rdd.RDD
import scala.reflect.ClassTag
import java.io.InputStream

object Implicits {

  implicit class SparkContextExt(val sc: SparkContext) {
    def protobuf[K <: Message : ClassTag](parser: InputStream => K): ProtobufRDDReader[K] =
      new ProtobufRDDReader(sc, parser)
  }

  implicit class RDDExt[K <: Message : ClassTag](val rdd: RDD[K]) {
    val protobuf: ProtobufRDDWriter[K] = new ProtobufRDDWriter(rdd)
  }
}

