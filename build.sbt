
enablePlugins(ProtobufPlugin)

version in ProtobufConfig := "3.6.0"

sourceDirectory in ProtobufConfig := (sourceDirectory in Test).value / "protobuf"

protobufIncludePaths in ProtobufConfig += (sourceDirectory in ProtobufConfig).value

fork in Test := true

javaOptions ++= Seq("-Xms512M", "-Xmx2048M", "-XX:MaxPermSize=2048M", "-XX:+CMSClassUnloadingEnabled")

scalaVersion := "2.11.12"
libraryDependencies := Seq(
  "org.apache.spark" %% "spark-core" % "2.2.3" % "provided",
  "org.scalatest" %% "scalatest" % "3.0.5" % "test",
  "com.google.protobuf" % "protobuf-java" % "3.6.1",
  "org.scalatest" %% "scalatest" % "3.0.5" % Test,
  "com.holdenkarau" %% "spark-testing-base" % "2.2.1_0.10.0" % Test)


