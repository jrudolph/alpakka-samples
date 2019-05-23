import scala.meta._
import sbt._

object Dependencies {

  object HttpCsvToKafka {
    val versions = {
      val source = IO.read(file(".") / ".." / "alpakka-sample-http-csv-to-kafka" / "project" / "Dependencies.scala")
      val tree = source.parse[Source].get
      tree.collect {
        case q"val ${v: Pat.Var} = ${s: Lit.String}" => v.name.value -> s.value
      }.toMap
    }

    val ScalaVersion = versions("scalaVer")
    val ScalaTestVersion = versions("ScalaTestVersion")
    val AkkaVersion = versions("AkkaVersion")
    val AkkaHttpVersion = versions("AkkaHttpVersion")
    val AlpakkaVersion = versions("AlpakkaVersion")
    val AlpakkaKafkaVersion = versions("AlpakkaKafkaVersion")
  }

}