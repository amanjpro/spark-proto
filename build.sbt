organization in ThisBuild := "me.amanj"

version in ThisBuild := "0.0.2-SNAPSHOT"

scalacOptions in ThisBuild ++= Seq(
  "-deprecation",                      // Emit warning and location for usages of deprecated APIs.
  "-explaintypes",                     // Explain type errors in more detail.
  "-feature",                          // Emit warning and location for usages of features that should be imported explicitly.
  "-unchecked",                        // Enable additional warnings where generated code depends on assumptions.
)

licenses in ThisBuild += ("Apache-2.0", url("https://opensource.org/licenses/Apache-2.0"))

skip in publish := true

fork in Test := true

javaOptions in ThisBuild ++= Seq("-Xms512M", "-Xmx2048M", "-XX:MaxPermSize=2048M", "-XX:+CMSClassUnloadingEnabled")

def getSparkDependencies(sparkVersion: String) = sparkVersion match {
  case "2_2"=>
    Seq("org.apache.spark" %% "spark-core" % "2.2.3" % "provided",
    "com.holdenkarau" %% "spark-testing-base" % "2.2.1_0.10.0" % Test)
}

def mkSparkProject(sversion: String, sparkVersion: String) = {
  val Array(major, minor, _) = sversion.split('.')
  val projectId = s"spark_${sparkVersion}_${major}_$minor"
  Project(id = projectId, base = file(s"spark_$sparkVersion")).settings(Seq(
    name := s"spark_${sparkVersion.replaceAll("_", ".")}",
    scalaVersion := sversion,
		target := baseDirectory.value / s"target-$sparkVersion-${scalaVersion.value}",
		skip in publish := true,
    libraryDependencies := getSparkDependencies(sparkVersion)
  ))
}

def mkProtoProject(sversion: String, sparkVersion: String) = {
  val Array(major, minor, _) = sversion.split('.')
  val projectId = s"proto_${sparkVersion}_${major}_$minor"
  Project(id = projectId, base = file("proto")).settings(Seq(
    name := s"spark-proto_${sparkVersion.replaceAll("_", ".")}",
    scalaVersion := sversion,
		skip in publish := false,
		sourceDirectory in ProtobufConfig := (sourceDirectory in Test).value / "protobuf",
		protobufIncludePaths in ProtobufConfig += (sourceDirectory in ProtobufConfig).value,
		version in ProtobufConfig := "3.6.0",
		target := baseDirectory.value / s"target-$sparkVersion-${scalaVersion.value}",
    publishMavenStyle := false,
		bintrayRepository := "maven",
		bintrayOrganization in bintray := None,
    libraryDependencies := Seq(
      "org.scalatest" %% "scalatest" % "3.0.5" % "test",
      "com.google.protobuf" % "protobuf-java" % "3.6.1",
      "org.scalatest" %% "scalatest" % "3.0.5" % Test) ++ getSparkDependencies(sparkVersion)
  )).enablePlugins(ProtobufPlugin)
}

lazy val spark_211_22 = mkSparkProject("2.11.12", "2_2")
lazy val spark_210_22 = mkSparkProject("2.10.7", "2_2")

lazy val proto_211_22 = mkProtoProject("2.11.12", "2_2").dependsOn(spark_211_22)
lazy val proto_210_22 = mkProtoProject("2.10.7", "2_2").dependsOn(spark_210_22)

unmanagedClasspath in Compile ++=
  update.value.select(configurationFilter("compileonly"))
