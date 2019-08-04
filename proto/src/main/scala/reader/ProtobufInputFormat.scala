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
package me.amanj.spark.proto.reader

import org.apache.hadoop.mapreduce.lib.input.FileInputFormat
import org.apache.hadoop.mapreduce.{TaskAttemptContext, RecordReader, InputSplit, JobContext}
import org.apache.hadoop.io.NullWritable
import org.apache.hadoop.fs.Path
import com.google.protobuf.Message
import java.io.InputStream

class ProtobufInputFormat[K <: Message](parser: InputStream => K)
    extends FileInputFormat[K, NullWritable] with Serializable {
  // Even though it might be not so efficient, we do not let hadoop/spark to
  // split protobuf files, that is how the record reader is set to work
  override def isSplitable(job: JobContext, path: Path): Boolean = false

  override def createRecordReader(split: InputSplit,
    context: TaskAttemptContext): RecordReader[K, NullWritable] = {
    new ProtobufRecordReader[K](parser)
  }
}
