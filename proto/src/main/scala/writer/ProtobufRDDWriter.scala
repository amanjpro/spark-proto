/*
 * Licensed to Amanj Sherwany <<http://amanj.me>>
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
package me.amanj.spark.proto.writer

import org.apache.spark.SparkContext
import org.apache.hadoop.io.NullWritable
import org.apache.hadoop.mapreduce.Job
import org.apache.spark.rdd.RDD
import com.google.protobuf.Message
import scala.reflect.{ClassTag, classTag}

class ProtobufRDDWriter[K <: Message : ClassTag](rdd: RDD[K]) {
  def write(output: String): Unit = {
    val ctx = rdd.sparkContext
    val job = Job.getInstance(ctx.hadoopConfiguration)
    val conf = job.getConfiguration

    rdd.map((_, NullWritable.get))
      .saveAsNewAPIHadoopFile(output,
        classTag[K].runtimeClass,
        classOf[NullWritable],
        classOf[ProtobufOutputFormat[K]],
        conf)
  }
}


