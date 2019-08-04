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
package me.amanj.spark.proto.writer

import org.apache.spark.rdd.RDD
import com.holdenkarau.spark.testing.{RDDComparisons, SharedSparkContext}
import org.scalatest.FunSuite
import me.amanj.spark.proto.PairOuterClass.Pair
import me.amanj.spark.proto.Implicits._

import org.scalatest.BeforeAndAfterEach
import java.nio.file.Files
import java.io.{FileOutputStream, File}


class ProtobufRDDWriterTest extends FunSuite
    with SharedSparkContext with RDDComparisons with BeforeAndAfterEach {
  val output: String = "spark-proto-test"

  override def afterEach(): Unit = {
    new File(output).delete()
  }

  test("Shall correctly write protobuf RDD to the disk") {
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

    val expected: RDD[Pair] = sc.parallelize(values)

    expected.protobuf.write(output)
    val actual: RDD[Pair] = sc.protobuf(Pair.parseDelimitedFrom).read(output)

    assertRDDEquals(expected, actual)
  }
}
