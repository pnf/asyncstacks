ThisBuild / version := "0.1.0-SNAPSHOT"

ThisBuild / scalaVersion := "2.13.8"

lazy val root = (project in file("."))
  .settings(
    name := "asyncstack"
  )

lazy val asyncMacros = (project in file("asyncMacros"))
  .settings(
    name := "asyncMacros",
    libraryDependencies += "com.lihaoyi" %% "sourcecode" % "0.2.7",
    libraryDependencies += "org.scala-lang.modules" %% "scala-async" % "0.10.0",
    libraryDependencies += "org.scala-lang" % "scala-reflect" % scalaVersion.value % Provided
  )

lazy val funcFun = (project in file("funcFun"))
  .dependsOn(asyncMacros)
  .settings(
    name := "funcFun",
    libraryDependencies += "com.lihaoyi" %% "sourcecode" % "0.2.7",
    libraryDependencies += "org.scala-lang.modules" %% "scala-async" % "0.10.0",
    libraryDependencies += "org.scala-lang" % "scala-reflect" % scalaVersion.value % Provided
  )


