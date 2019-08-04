# Protobuf3 Data Source for Apache Spark

A library for reading and writing Protobuf3 data from [Spark
RDD](http://spark.apache.org/docs/latest/sql-programming-guide.html).

[![Build Status](https://travis-ci.org/amanjpro/spark-proto.svg?branch=master)](https://travis-ci.org/amanjpro/spark-proto)

## Requirements

This documentation is for version 0.0.5 of this library, which supports Spark
1.6.x, 2.0.x, 2.1.x, 2.2.x, 2.3.x and 2.4.x.

## Linking

This library is cross-published for Scala 2.10 (for Spark versions upto 2.2)
and Scala 2.11 (from Scala 2.0 and up).

You can link against this library in your program at the following coordinates:

**Using SBT:**

```
libraryDependencies += "me.amanj" %% "proto_$SPARK_VERSION" % "0.0.5"
```

Where `$SPARK_VERSION` is one of the following:

| Spark Version   | $SPARK_VERSION Value |
| --------------- | -------------------- |
| `1.6.x`         | `1-6`                |
| `2.0.x`         | `2-0`                |
| `2.1.x`         | `2-1`                |
| `2.2.x`         | `2-2`                |
| `2.3.x`         | `2-3`                |
| `2.4.x`         | `2-4`                |


**Using Maven:**

```xml
<dependency>
    <groupId>me.amanj</groupId>
    <artifactId>$SPARK_VERSION_$SCALA_VERSION</artifactId>
    <version>0.0.5</version>
</dependency>
```

Where Scala version is either `2.10` or `2.11`.

### With `spark-shell` or `spark-submit`

This library can also be added to Spark jobs launched through `spark-shell` or
`spark-submit` by using the `--packages` command line option.  For example, to
include it when starting the spark shell:

```
$ bin/spark-shell --packages me.amanj:proto_2-4_2.11:0.0.5
```

Unlike using `--jars`, using `--packages` ensures that this library and its
dependencies will be added to the classpath. The `--packages` argument can also
be used with `bin/spark-submit`.

## Features

Proto Data Source for Spark supports reading and writing of Protobuf3 data from Spark.

## Examples

Given the following Protobuf3 Schema:

```protobuf
syntax = "proto3";

package me.amanj.spark.proto;

message Pair {
  string first = 1;
  int32 second = 2;
}
```

You can use the library as follows:

```scala
// import needed for the .avro method to be added
import me.amanj.spark.proto.Implicits._
import me.amanj.spark.proto.PairOuterClass.Pair
import org.apache.spark.SparkContext

// Loading Protobuf3 objects into an RDD
val rdd: RDD[Pair] = sc.protobuf(Pair.parseDelimitedFrom).read("test")

// Writing an RDD into Protobuf3 files
rdd.protobuf.write("another-test")
```

## Building From Source
This library is built with [SBT](http://www.scala-sbt.org). To build a JAR file
simply run `sbt clean package` from the project root.

To run the tests, you should run `sbt test`, to compile run `sbt compile`.

## LICENSE

```
Licensed to Amanj Sherwany <<http://amanj.m>>

The ASF licenses this file to You under the Apache License, Version 2.0
(the "License"); you may not use this file except in compliance with
the License.  You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```
