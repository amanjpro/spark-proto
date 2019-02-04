package me.amanj.spark.proto.reader

import com.google.protobuf.Message
import org.apache.spark.SparkContext
import scala.reflect.ClassTag
import org.apache.spark.rdd.RDD

object Implicits {

  implicit class SparkContextExt(val sc: SparkContext) extends AnyVal {
    def protobuf[K <: Message : ClassTag, F <: ProtobufInputFormat[K] : ClassTag](input: String): RDD[K] = {
      new ProtobufRDDReader[K, F](sc).read(input)
    }
  }
}

