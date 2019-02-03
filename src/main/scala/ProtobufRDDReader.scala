package me.amanj.spark.protobuf.reader


import org.apache.hadoop.mapreduce.lib.input.{FileSplit,FileInputFormat}
import org.apache.hadoop.mapreduce.{TaskAttemptContext, RecordReader, InputSplit, JobContext, Job}
import org.apache.hadoop.io.NullWritable
import org.apache.hadoop.fs.{Path,FSDataInputStream}
import org.apache.spark.SparkContext
import org.apache.spark.rdd.RDD
import com.google.protobuf.Message
import java.util.UnknownFormatConversionException
import scala.reflect.ClassTag

object PBRDDReader {

  private[this] def read[K <: Message, W <: FileInputFormat[K, NullWritable]](
      ctx: SparkContext, input: String, keyClass: Class[K],
      readerClass: Class[W]): RDD[(K, NullWritable)] = {

    val hadoopConf = ctx.hadoopConfiguration
    val job = Job.getInstance
    job.setInputFormatClass(readerClass)
    val hadoopPath = new Path(input)
    FileInputFormat.setInputDirRecursive(job, true)
    hadoopConf.addResource(job.getConfiguration)

    // I need to get the FileSystem that stupid way to handle both
    // S3 and HDFS file systems.
    // -- Amanj
    if (hadoopPath.getFileSystem(hadoopConf).exists(hadoopPath)) {
      ctx.newAPIHadoopFile(input,
                           readerClass,
                           keyClass,
                           classOf[NullWritable],
                           hadoopConf)
    } else {
      ctx.emptyRDD[(K, NullWritable)]
    }
  }

  def read[K <: Message](ctx: SparkContext, input: String)(implicit ktag: ClassTag[K]): RDD[K] = {
    read[K, PBInputFormat[K]](ctx, input, ktag.runtimeClass,
      classOf[PBInputFormat[K]]).map { case (k, _) => k }
  }

}

class PBInputFormat[K <: Message] extends FileInputFormat[K, NullWritable] {
  // Even though it might be not so efficient, we do not let hadoop/spark to
  // split protobuf files, that is how the record reader is set to work
  override def isSplitable(job: JobContext, path: Path): Boolean = false

  override def createRecordReader(split: InputSplit,
    context: TaskAttemptContext): RecordReader[K, NullWritable] = {
    new PBRecordReader
  }
}

class PBRecordReader[K <: Message] extends RecordReader[K, NullWritable] {

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

  override def nextKeyValue(): Boolean = ???

  override def getCurrentKey(): K = key

  override def getCurrentValue(): NullWritable = value

  override def getProgress(): Float = {
    if (start == end) {
        0.0f;
    } else {
        1.0f min ((pos - start) * 1.0f / (end - start))
    }
  }

  override def close(): Unit = Option(in).foreach(_.close)
}
