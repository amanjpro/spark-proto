// package com.adgear.data.segmentetl.io
//
// import org.apache.hadoop.fs.{FSDataOutputStream, Path}
// import org.apache.hadoop.io.{BytesWritable, LongWritable}
// import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat
// import org.apache.hadoop.mapreduce.{Job, RecordWriter, TaskAttemptContext}
// import org.apache.spark.SparkContext
// import org.apache.spark.rdd.RDD
//
// object ProtobufRDDWriter {
//   def write(ctx: SparkContext, output: String, rdd: RDD[(BytesWritable, BytesWritable)]): Unit = {
//     val job = Job.getInstance(ctx.hadoopConfiguration)
//     val conf = job.getConfiguration
//
//     rdd.saveAsNewAPIHadoopFile(output,
//       classOf[BytesWritable],
//       classOf[BytesWritable],
//       classOf[BytesWritablePairOutputFormat],
//       conf)
//   }
// }
//
// class BytesWritablePairOutputFormat extends FileOutputFormat[BytesWritable, BytesWritable] {
//   def getRecordWriter(context: TaskAttemptContext): RecordWriter[BytesWritable, BytesWritable] = {
//     val path = FileOutputFormat.getOutputPath(context)
//
//     val fullPath = new Path(path, f"part-${context.getTaskAttemptID.getTaskID.getId}%05d.bin")
//     val fs = path.getFileSystem(context.getConfiguration)
//     val fileOut = fs.create(fullPath, context)
//
//     getRecordWriter(fileOut)
//   }
//
//   def getRecordWriter(out: FSDataOutputStream): RecordWriter[BytesWritable, BytesWritable] = {
//     new RecordWriter[BytesWritable, BytesWritable]() {
//       override def close(context: TaskAttemptContext): Unit = out.close()
//
//       override def write(key: BytesWritable, value: BytesWritable): Unit = {
//         out.write(key.getBytes)
//         out.write(value.getBytes)
//       }
//     }
//   }
// }
