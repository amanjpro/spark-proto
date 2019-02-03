package me.amanj.spark.proto.reader

import com.google.protobuf.Message
import org.apache.spark.SparkContext
import scala.reflect.ClassTag

object Implicits {

  implicit class SparkContextExt(val sc: SparkContext) extends AnyVal {
    def protobuf[K <: Message : ClassTag](clazz: Class[_ <: ProtobufInputFormat[K]]): ProtobufRDDReader[K] = {
      new ProtobufRDDReader[K](sc, clazz)
    }
  }
}

