organization in ThisBuild := "me.amanj"

version in ThisBuild := "0.0.4-SNAPSHOT"

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

javaOptions in ThisBuild ++= Seq("-Xms512M", "-Xmx2048M", "-XX:MaxPermSize=2048M", "-XX:+CMSClassUnloadingEnabled")

def getSparkDependencies(sparkVersion: String) = sparkVersion match {
  case "2.2"=>
    Seq("org.apache.spark" %% "spark-core" % "2.2.3",
    "com.holdenkarau" %% "spark-testing-base" % "2.2.1_0.10.0" % Test)
  case "1.6"=>
    Seq("org.apache.spark" %% "spark-core" % "1.6.3",
    "com.holdenkarau" %% "spark-testing-base" % "1.6.1_0.10.0" % Test)
}

def mkSparkProject(sversion: String, sparkVersion: String) = {
  val Array(major, minor, _) = sversion.split('.')
  val projectId = s"spark_${sparkVersion.replaceAll("[.]", "_")}_${major}_$minor"
  val projectBase = s"spark_${sparkVersion.replaceAll("[.]", "_")}"
  val projectName = s"spark_${sparkVersion.replaceAll("[.]", "_")}_${major}.$minor"
  val projectTarget = s"target-$sparkVersion-$sversion"
  Project(id = projectId, base = file(projectBase)).settings(Seq(
    name := projectName,
    scalaVersion := sversion,
    skip in publish := true,
    target := baseDirectory.value / projectTarget,
    libraryDependencies ++= getSparkDependencies(sparkVersion)
  )).disablePlugins(BintrayPlugin)
}

def mkProtoProject(sversion: String, sparkVersion: String) = {
  val Array(major, minor, _) = sversion.split('.')
  val projectId = s"proto_${sparkVersion.replaceAll("[.]", "_")}_${major}_$minor"
  val projectName = s"spark-proto_${sparkVersion.replaceAll("[.]", "_")}_${major}.$minor"
  val projectTarget = s"target-$sparkVersion-$sversion"
  Project(id = projectId, base = file("proto")).settings(Seq(
    name := projectName,
    scalaVersion := sversion,
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
lazy val spark_211_16 = mkSparkProject("2.11.12", "1.6")
lazy val proto_211_16 = mkProtoProject("2.11.12", "1.6").dependsOn(spark_211_16)

lazy val spark_210_16 = mkSparkProject("2.10.7", "1.6")
lazy val proto_210_16 = mkProtoProject("2.10.7", "1.6").dependsOn(spark_210_16)

// Spark 2.2.x
lazy val spark_211_22 = mkSparkProject("2.11.12", "2.2")
lazy val proto_211_22 = mkProtoProject("2.11.12", "2.2").dependsOn(spark_211_22)

lazy val spark_210_22 = mkSparkProject("2.10.7", "2.2")
lazy val proto_210_22 = mkProtoProject("2.10.7", "2.2").dependsOn(spark_210_22)
