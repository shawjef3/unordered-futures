enablePlugins(JmhPlugin)

organization := "me.jeffshaw.futures"

organizationName := "me.jeffshaw.futures"

organizationHomepage := Some(url("https://www.github.com/shawjef3/"))

name := "unordered"

version := "1.0"

description := "A Scala library for quickly aggregating iterators of futures."

scmInfo :=
  Some(
    ScmInfo(
      browseUrl = url("https://github.com/shawjef3/unordered-futures"),
      connection = "git@github.com:shawjef3/unordered-futures.git"
    )
  )

developers := List(
  Developer(
    id = "shawjef3",
    name = "Jeffrey Shaw",
    email = "shawjef3@gmail.com",
    url = url("https://www.github.com/shawjef3/")
  )
)

licenses := Seq("The BSD 3-Clause License" -> url("http://opensource.org/licenses/BSD-3-Clause"))

publishMavenStyle := true

scalaVersion := "2.11.12"

crossScalaVersions := Seq("3.3.0", "2.13.11", "2.12.18")

libraryDependencies ++= Seq(
  "org.scalatest" %% "scalatest" % "3.2.16" % Test
)
