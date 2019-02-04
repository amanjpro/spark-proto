package me.amanj.spark.proto.reader

import org.apache.spark.rdd.RDD
import com.holdenkarau.spark.testing.{RDDComparisons, SharedSparkContext}
import org.scalatest.{FunSuite, Matchers}
import me.amanj.spark.proto.PairOuterClass.Pair
import me.amanj.spark.proto.reader.Implicits._

import java.io.{InputStream, FileOutputStream}

class PairProtobufInputFormat extends ProtobufInputFormat[Pair] {
  val parser = (io: InputStream) => Pair.parseDelimitedFrom(io)
}

class ProtobufRDDReaderTest extends FunSuite with SharedSparkContext with RDDComparisons {
  test("Shall correctly load protobuf text files into an RDD") {
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

    val dos = new FileOutputStream("test")
    values.foreach { pb =>
      pb.writeDelimitedTo(dos)
    }
    dos.close

    val expected: RDD[Pair] = sc.parallelize(values)

    val reader: ProtobufRDDReader[Pair] = sc.protobuf

    val actual: RDD[Pair] = reader.read("test")

    assertRDDEquals(expected, actual)
  }
}
