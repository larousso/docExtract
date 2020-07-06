inThisBuild(
  Seq(
    version in ThisBuild := "0.0.4",
    organization in ThisBuild := "com.adelegue"
  )
)

lazy val bintrayCommonSettings = Seq(
  publishMavenStyle := false,
  licenses += ("MIT", url("https://opensource.org/licenses/MIT")),
  bintrayVcsUrl := Some("git@github.com:larousso/docExtract.git"),
  bintrayRepository := "sbt-plugins",
  bintrayOrganization in bintray := None
)

resolvers += Resolver.bintrayRepo("scalaz", "releases")

resolvers += Resolver.typesafeRepo("releases")

lazy val noPublishSettings = Seq(
  publish := (),
  publishLocal := (),
  publishArtifact := false
)

lazy val docExtract = project.in(file("."))
  .aggregate(core, plugin)
  .settings(sourcesInBase := false)
  .settings(noPublishSettings: _*)

lazy val core = project.in(file("core"))
  .settings(
    bintrayCommonSettings,
    name := "doc-extract",
    scalaVersion := "2.13.3",
    crossScalaVersions := Seq("2.11.11", "2.12.9", "2.13.3"),
    libraryDependencies += {
      CrossVersion.partialVersion(scalaVersion.value) match {
        case Some((major, 11)) =>
          "org.scala-lang" % s"scala-compiler" % s"$major.11.11"
        case Some((major, 12)) =>
          "org.scala-lang" % s"scala-compiler" % s"$major.12.9"
        case Some((major, 13)) =>
          "org.scala-lang" % s"scala-compiler" % s"$major.13.3"
        case x =>
          throw new IllegalArgumentException(s"Please improve the cross build setting, $x is unexpected.")
      }
    },
    libraryDependencies += "org.scalatest" %% "scalatest" % "3.0.8" % "test"
  )

lazy val plugin = project.in(file("sbtPlugin"))
  .enablePlugins(BuildInfoPlugin)
  .settings(
    buildInfoKeys := Seq[BuildInfoKey](name, version, scalaVersion, sbtVersion),
    buildInfoPackage := "com.adelegue.doc.extract"
  )
  .settings(
    bintrayCommonSettings,
    name := "sbt-doc-extract",
    description := "Extract case class and properties description from scaladoc as a resource bundle.",
    sbtPlugin := true
  )
