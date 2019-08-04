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

import com.google.protobuf.Message
import org.apache.hadoop.io.NullWritable
import org.apache.hadoop.mapreduce.{TaskAttemptContext, RecordWriter}
import org.apache.hadoop.fs.Path
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat

class ProtobufOutputFormat[K <: Message] extends FileOutputFormat[K, NullWritable] {

  override def getRecordWriter(
    context: TaskAttemptContext): RecordWriter[K, NullWritable] = {
    val path = FileOutputFormat.getOutputPath(context)

    val fullPath = new Path(path,
      f"part-${context.getTaskAttemptID.getTaskID.getId}%05d.pb")

    val fs = path.getFileSystem(context.getConfiguration)
    val fileOut = fs.create(fullPath, context)

    new RecordWriter[K, NullWritable] {
      def write(key: K, ignore: NullWritable): Unit = {
        key.writeDelimitedTo(fileOut)
      }

      def close(context: TaskAttemptContext): Unit = {
        fileOut.close
      }
    }

  }
}

