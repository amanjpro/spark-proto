package me.amanj.spark.proto.reader

import java.io.InputStream
import com.google.protobuf.Message
import org.apache.spark.SparkContext
import org.apache.spark.rdd.SparkContextExt
import reflect.ClassTag
import org.apache.spark.rdd.RDDOperationScope
import scala.reflect.runtime.universe._
import org.apache.spark.rdd.RDD
import java.io.{FileNotFoundException, IOException}
import java.text.SimpleDateFormat
import java.util.{Date, Locale}

import scala.collection.JavaConverters.asScalaBufferConverter
import scala.reflect.ClassTag

import com.google.protobuf.Message

import org.apache.hadoop.conf.{Configurable, Configuration}
import org.apache.hadoop.io.Writable
import org.apache.hadoop.io.NullWritable
import org.apache.hadoop.mapred.JobConf
import org.apache.hadoop.mapreduce._
import org.apache.hadoop.mapreduce.lib.input.{CombineFileSplit, FileInputFormat, FileSplit, InvalidInputException}
import org.apache.hadoop.mapreduce.task.{JobContextImpl, TaskAttemptContextImpl}

import org.apache.spark._
import org.apache.spark.annotation.DeveloperApi
import org.apache.spark.deploy.SparkHadoopUtil
import org.apache.spark.internal.Logging
import org.apache.spark.internal.config._
import org.apache.spark.storage.StorageLevel
import org.apache.spark.util.{SerializableConfiguration, ShutdownHookManager}



object Implicits {

  implicit class SparkContextExtImpl(val sc: SparkContext) {
    def protobuf[K <: Message : ClassTag](parser: InputStream => K): ProtobufRDDReader[K] =
      new ProtobufRDDReader(sc, parser)
  }
}

