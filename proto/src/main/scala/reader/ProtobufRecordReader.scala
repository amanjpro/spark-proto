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

import org.apache.hadoop.mapreduce.lib.input.FileSplit
import org.apache.hadoop.mapreduce.{TaskAttemptContext, RecordReader, InputSplit}
import org.apache.hadoop.io.NullWritable
import org.apache.hadoop.fs.FSDataInputStream
import com.google.protobuf.Message
import java.io.InputStream

class ProtobufRecordReader[K <: Message](parser: InputStream => K) extends RecordReader[K, NullWritable] {

  protected var start: Long = _
  protected var end: Long   = _
  protected var pos: Long = _

  protected var key: K   = _
  protected val value: NullWritable = NullWritable.get
  protected var in: FSDataInputStream = _

  override def initialize(genericSplit: InputSplit , context: TaskAttemptContext): Unit =
    genericSplit match {
      case split: FileSplit =>
        val job = context.getConfiguration

        val file = split.getPath
        val fs = file.getFileSystem(job)
        start = split.getStart
        pos = start
        end = start + split.getLength
        in = fs.open(file)
        in.seek(start)
      case _                =>
        throw new IllegalArgumentException("Only FileSplit is supported")
    }

  override def nextKeyValue(): Boolean = {
    val result = parser(in)
    if(result == null) false
    else {
      key = result
      true
    }
  }

  override def getCurrentKey(): K = key

  override def getCurrentValue(): NullWritable = value

  override def getProgress(): Float = {
    if (start == end) {
        1.0f;
    } else {
        1.0f min ((pos - start) * 1.0f / (end - start))
    }
  }

  override def close(): Unit = Option(in).foreach(_.close)
}
