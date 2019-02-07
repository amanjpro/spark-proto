package me.amanj.spark.proto.reader


import org.apache.hadoop.mapreduce.lib.input.{FileSplit,FileInputFormat}
import org.apache.hadoop.mapreduce.{TaskAttemptContext, RecordReader, InputSplit, JobContext, Job}
import org.apache.hadoop.io.NullWritable
import org.apache.hadoop.fs.{Path,FSDataInputStream}
import org.apache.spark.SparkContext
import org.apache.spark.rdd.RDD
import com.google.protobuf.{Message, Parser}
import java.util.UnknownFormatConversionException
import java.io.InputStream
import scala.reflect.{ClassTag, classTag}
import scala.reflect.runtime.universe.TypeTag

class Protobuf[K <: Message : ClassTag](sc: SparkContext) {
  def read[F <: ProtobufInputFormat[K] : ClassTag : TypeTag](path: String): RDD[K] = {
    new ProtobufRDDReader[K](sc).read(path)
  }
}

class ProtobufRDDReader[K <: Message](sc: SparkContext)(implicit kTag: ClassTag[K]) {

  private[this] def readImpl[F <: ProtobufInputFormat[K]](input: String)(implicit fTag: ClassTag[F]): RDD[(K, NullWritable)] = {
    println(s"K: $kTag, F: $fTag")
    val hadoopConf = sc.hadoopConfiguration
    val hadoopPath = new Path(input)

    // I need to get the FileSystem that stupid way to handle both
    // S3 and HDFS file systems.
    // -- Amanj
    if (hadoopPath.getFileSystem(hadoopConf).exists(hadoopPath)) {
      sc.newAPIHadoopFile(input)
    } else {
      sc.emptyRDD[(K, NullWritable)]
    }
  }

  def read[F <: ProtobufInputFormat[K]: ClassTag](input: String): RDD[K] =
    readImpl(input)
      .map { case (k, _) => k }
}

trait ProtobufInputFormat[K <: Message] extends FileInputFormat[K, NullWritable] {
  def parser: InputStream => K
  // Even though it might be not so efficient, we do not let hadoop/spark to
  // split protobuf files, that is how the record reader is set to work
  override def isSplitable(job: JobContext, path: Path): Boolean = false

  override def createRecordReader(split: InputSplit,
    context: TaskAttemptContext): RecordReader[K, NullWritable] = {
    new ProtobufRecordReader[K](parser)
  }
}

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
