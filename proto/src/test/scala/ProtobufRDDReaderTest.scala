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

import org.apache.spark.rdd.RDD
import com.holdenkarau.spark.testing.{RDDComparisons, SharedSparkContext}
import org.scalatest.FunSuite
import me.amanj.spark.proto.PairOuterClass.Pair
import me.amanj.spark.proto.Implicits._

import java.io.{FileOutputStream, File}
import org.scalatest.BeforeAndAfterEach
import java.nio.file.Files

class ProtobufRDDReaderTest extends FunSuite
  with SharedSparkContext with RDDComparisons with BeforeAndAfterEach {
  var output: String = _

  override def beforeEach(): Unit = {
    output = Files.createTempDirectory("spark-proto").toString
  }

  override def afterEach(): Unit = {
    new File(output).delete()
  }

  test("Shall correctly load protobuf files into an RDD") {
    val values = List(
        ("one", 1),
        ("two", 2),
        ("three", 3),
        ("four", 4),
        ("five", 5)
      ).map { case (f, s) =>
        Pair.newBuilder()
          .setFirst(f)
          .setSecond(s)
          .build
      }

    val dos = new FileOutputStream(s"$output/part-00000.pb")
    values.foreach { pb =>
      pb.writeDelimitedTo(dos)
    }
    dos.close

    val expected: RDD[Pair] = sc.parallelize(values)

    val actual: RDD[Pair] = sc.protobuf(Pair.parseDelimitedFrom).read(output)

    assertRDDEquals(expected, actual)
  }


  test("getProgress should work when start and end are the same") {
    val recordReader = new ProtobufRecordReader[Pair](_ => null) {
      start = 10
      end = 10
    }
    assert(recordReader.getProgress(), 1.0f)
  }

  test("getProgress should work when midway") {
    val recordReader = new ProtobufRecordReader[Pair](_ => null) {
      start = 0
      end = 10
      pos = 5
    }
    assert(recordReader.getProgress(), 0.5f)
  }

  test("getProgress should work when done") {
    val recordReader = new ProtobufRecordReader[Pair](_ => null) {
      start = 0
      end = 10
      pos = 10
    }
    assert(recordReader.getProgress(), 1.0f)
  }

  test("getProgress should work when it has not started") {
    val recordReader = new ProtobufRecordReader[Pair](_ => null) {
      start = 0
      end = 10
      pos = 0
    }
    assert(recordReader.getProgress(), 0.0f)
  }
}
