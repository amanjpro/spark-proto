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

scalaVersion in ThisBuild := "2.11.12"

crossScalaVersions in ThisBuild := Seq("2.11.12", "2.10.7")

javaOptions in ThisBuild ++= Seq("-Xms512M", "-Xmx2048M", "-XX:MaxPermSize=2048M", "-XX:+CMSClassUnloadingEnabled")

def getSparkDependencies(sparkVersion: String) = sparkVersion match {
  case "2.3"=>
    Seq("org.apache.spark" %% "spark-core" % "2.3.3",
    "com.holdenkarau" %% "spark-testing-base" % "2.3.2_0.11.0" % Test)
  case "2.2"=>
    Seq("org.apache.spark" %% "spark-core" % "2.2.3",
    "com.holdenkarau" %% "spark-testing-base" % "2.2.2_0.11.0" % Test)
  case "2.1"=>
    Seq("org.apache.spark" %% "spark-core" % "2.1.3",
    "com.holdenkarau" %% "spark-testing-base" % "2.1.3_0.11.0" % Test)
  case "1.6"=>
    Seq("org.apache.spark" %% "spark-core" % "1.6.3",
    "com.holdenkarau" %% "spark-testing-base" % "1.6.3_0.11.0" % Test)
}

def mkSparkProject(sparkVersion: String) = {
  val projectId = s"spark_${sparkVersion.replaceAll("[.]", "_")}"
  val projectBase = projectId
  val projectName = projectId
  val projectTarget = s"target-$sparkVersion"
  Project(id = projectId, base = file(projectBase)).settings(Seq(
    name := projectName,
    skip in publish := true,
    crossPaths in ThisBuild := true,
    target := baseDirectory.value / projectTarget,
    libraryDependencies ++= getSparkDependencies(sparkVersion)
  )).disablePlugins(BintrayPlugin)
}

def mkProtoProject(sparkVersion: String) = {
  val projectId = s"proto_${sparkVersion.replaceAll("[.]", "_")}"
  val projectName = s"spark-$projectId"
  val projectTarget = s"target-$sparkVersion"
  Project(id = projectId, base = file("proto")).settings(Seq(
    name := projectName,
    skip in publish := false,
    sourceDirectory in ProtobufConfig := (sourceDirectory in Test).value / "protobuf",
    protobufIncludePaths in ProtobufConfig += (sourceDirectory in ProtobufConfig).value,
    version in ProtobufConfig := "3.6.1",
    target := baseDirectory.value / projectTarget,
    crossPaths in ThisBuild := true,
    bintrayRepository := "maven",
    bintrayOrganization in bintray := None,
    parallelExecution in Test := false,
    libraryDependencies ++= Seq(
      "com.google.protobuf" % "protobuf-java" % "3.6.1") ++ getSparkDependencies(sparkVersion)
  )).enablePlugins(ProtobufPlugin, BintrayPlugin)
}

// Spark 1.6.x
lazy val spark_1_6 = mkSparkProject("1.6")
lazy val proto_1_6 = mkProtoProject("1.6").dependsOn(spark_1_6)

// Spark 2.2.x
lazy val spark_2_2 = mkSparkProject("2.2")
lazy val proto_2_2 = mkProtoProject("2.2").dependsOn(spark_2_2)

// Spark 2.1.x
lazy val spark_2_1 = mkSparkProject("2.1")
lazy val proto_2_1 = mkProtoProject("2.1").dependsOn(spark_2_1)

// Spark 2.4.x
lazy val spark_2_3 = mkSparkProject("2.3")
lazy val proto_2_3 = mkProtoProject("2.3").dependsOn(spark_2_3)
