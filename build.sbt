organization in ThisBuild := "me.amanj"

version in ThisBuild := "0.0.5-SNAPSHOT"

scalacOptions in ThisBuild ++= Seq(
  "-deprecation",                      // Emit warning and location for usages of deprecated APIs.
  "-explaintypes",                     // Explain type errors in more detail.
  "-feature",                          // Emit warning and location for usages of features that should be imported explicitly.
  "-unchecked",                        // Enable additional warnings where generated code depends on assumptions.
)

licenses in ThisBuild += ("Apache-2.0", url("https://opensource.org/licenses/Apache-2.0"))


fork in Test in ThisBuild := true

disablePlugins(BintrayPlugin)

skip in publish := true

val SCALA_211 = "2.11.12"
val SCALA_210 = "2.10.7"

javaOptions in ThisBuild ++= Seq("-Xms512M", "-Xmx2048M", "-XX:MaxPermSize=2048M", "-XX:+CMSClassUnloadingEnabled")

def getSparkDependencies(sparkVersion: String, scalaVersionFull: String) = {
  val scalaVersion = scalaVersionFull.replaceAll("\\.[0-9]+$", "")
  sparkVersion match {
    case "2.4"=>
      Seq("org.apache.spark" % s"spark-core_$scalaVersion" % "2.4.3",
      "com.holdenkarau" % s"spark-testing-base_$scalaVersion" % "2.4.2_0.12.0" % Test)
    case "2.3"=>
      Seq("org.apache.spark" % s"spark-core_$scalaVersion" % "2.3.3",
      "com.holdenkarau" % s"spark-testing-base_$scalaVersion" % "2.3.2_0.12.0" % Test)
    case "2.2"=>
      Seq("org.apache.spark" % s"spark-core_$scalaVersion" % "2.2.3",
      "com.holdenkarau" % s"spark-testing-base_$scalaVersion" % "2.2.2_0.12.0" % Test)
    case "2.1"=>
      Seq("org.apache.spark" % s"spark-core_$scalaVersion" % "2.1.3",
      "com.holdenkarau" % s"spark-testing-base_$scalaVersion" % "2.1.3_0.12.0" % Test)
    case "2.0"=>
      Seq("org.apache.spark" % s"spark-core_$scalaVersion" % "2.0.2",
      "com.holdenkarau" % s"spark-testing-base_$scalaVersion" % "2.0.2_0.12.0" % Test)
    case "1.6"=>
      Seq("org.apache.spark" % s"spark-core_$scalaVersion" % "1.6.3",
      "com.holdenkarau" % s"spark-testing-base_$scalaVersion" % "1.6.3_0.12.0" % Test)
  }
}

def mkSparkProject(sparkVersion: String, scalaVersionStr: String) = {
  val projectId = s"spark_${sparkVersion.replaceAll("[.]", "_")}"
  val projectBase = projectId
  val projectName = projectId
  val projectTarget = s"target-$sparkVersion"
  Project(id = projectId, base = file(projectBase)).settings(Seq(
    name := projectName,
    scalaVersion := scalaVersionStr,
    skip in publish := true,
    crossPaths in ThisBuild := true,
    target := baseDirectory.value / projectTarget,
    libraryDependencies ++= getSparkDependencies(sparkVersion, scalaVersionStr)
  )).disablePlugins(BintrayPlugin)
}

def mkProtoProject(sparkVersion: String, scalaVersionStr: String) = {
  val projectId = s"proto_${sparkVersion.replaceAll("[.]", "_")}"
  val projectName = s"spark-$projectId"
  val projectTarget = s"target-$sparkVersion"
  Project(id = projectId, base = file("proto")).settings(Seq(
    name := projectName,
    skip in publish := false,
    scalaVersion := scalaVersionStr,
    sourceDirectory in ProtobufConfig := (sourceDirectory in Test).value / "protobuf",
    protobufIncludePaths in ProtobufConfig += (sourceDirectory in ProtobufConfig).value,
    version in ProtobufConfig := "3.6.1",
    target := baseDirectory.value / projectTarget,
    crossPaths in ThisBuild := true,
    bintrayRepository := "maven",
    bintrayOrganization in bintray := None,
    parallelExecution in Test := false,
    libraryDependencies ++= Seq(
      "com.google.protobuf" % "protobuf-java" % "3.6.1") ++
        getSparkDependencies(sparkVersion, scalaVersionStr)
  )).enablePlugins(ProtobufPlugin, BintrayPlugin)
}

// Scala 2.10 only
// Spark 1.6.x, 2.10
lazy val spark_1_6 = mkSparkProject("1.6", SCALA_210)
lazy val proto_1_6 = mkProtoProject("1.6", SCALA_210).dependsOn(spark_1_6)

// Scala 2.10 and 2.11

// Spark 2.0.x, 2.10
lazy val spark_2_0_210 = mkSparkProject("2.0", SCALA_210)
lazy val proto_2_0_210 = mkProtoProject("2.0", SCALA_210).dependsOn(spark_2_0_210)

// Spark 2.1.x, 2.10
lazy val spark_2_1_210 = mkSparkProject("2.1", SCALA_210)
lazy val proto_2_1_210 = mkProtoProject("2.1", SCALA_210).dependsOn(spark_2_1_210)

// Spark 2.2.x, 2.10
lazy val spark_2_2_210 = mkSparkProject("2.2", SCALA_210)
lazy val proto_2_2_210 = mkProtoProject("2.2", SCALA_210).dependsOn(spark_2_2_210)

// Scala 2.11 only

// Spark 2.0.x, 2.11
lazy val spark_2_0_211 = mkSparkProject("2.0", SCALA_211)
lazy val proto_2_0_211 = mkProtoProject("2.0", SCALA_211).dependsOn(spark_2_0_211)

// Spark 2.1.x, 2.10
lazy val spark_2_1_211 = mkSparkProject("2.1", SCALA_211)
lazy val proto_2_1_211 = mkProtoProject("2.1", SCALA_211).dependsOn(spark_2_1_211)

// Spark 2.2.x, 2.10
lazy val spark_2_2_211 = mkSparkProject("2.2", SCALA_211)
lazy val proto_2_2_211 = mkProtoProject("2.2", SCALA_211).dependsOn(spark_2_2_211)

// Spark 2.3.x, 2.10
lazy val spark_2_3 = mkSparkProject("2.3", SCALA_211)
lazy val proto_2_3 = mkProtoProject("2.3", SCALA_211).dependsOn(spark_2_3)

// Spark 2.4.x, 2.10
lazy val spark_2_4 = mkSparkProject("2.4", SCALA_211)
lazy val proto_2_4 = mkProtoProject("2.4", SCALA_211).dependsOn(spark_2_4)
