package coffeescript

import scala.collection.JavaConversions._

import sbt._
import Keys._
import Project.Initialize

import java.nio.charset.Charset
import java.io.File

object CoffeeScript extends Plugin {

  val Coffee = config("coffee") extend(Runtime)

  type Compiler = { def compile(src: String): Either[String, String] }

  val coffee = TaskKey[Seq[File]]("coffee", "Compile coffee sources.")
  val clean = TaskKey[Unit]("clean", "Clean compiled coffee sources.")
  val sources = TaskKey[Seq[File]]("sources", "List of coffee source files")
  val sourceDirectory = SettingKey[File]("source-directory", "Directory containing coffee sources.")
  // think about changing to includeFilter in the next rel (maybe, I like the way coffee:filter sounds :))
  val filter = SettingKey[FileFilter]("filter", "Filter for selecting coffee sources from default directories.")
  val excludeFilter = SettingKey[FileFilter]("exclude-filter", "Filter for excluding files from default directories.")
  val targetDirectory = SettingKey[File]("target-directory", "Output directory for compiled coffee sources.")
  val bare = SettingKey[Boolean]("bare", "Compile coffee sources without top-level function wrapper.")
  val charset = SettingKey[Charset]("charset", "Sets the character encoding used in file generation. Defaults to utf-8")


  private def javascript(sources: File, coffee: File, targetDir: File) =
    Some(new File(targetDir, IO.relativize(sources, coffee).get.replace(".coffee",".js")))

  private def compile(compiler: Compiler, charset: Charset, out: Logger)(pair: (File, File)) =
    try {
      val (coffee, js) = pair
      out.debug("Compiling %s" format coffee)
      compiler.compile(io.Source.fromFile(coffee)(io.Codec(charset)).mkString).fold({ err =>
        error(err)
      }, { compiled =>
        IO.write(js, compiled)
        out.debug("Wrote to file %s" format js)
        js
      })
    } catch { case e: Exception =>
      throw new RuntimeException(
        "error occured while compiling %s: %s" format(pair._1, e.getMessage), e
      )
    }

  private def compiled(under: File) = (under ** "*.js").get

  private def compileChanged(sources: File, target: File, compiler: Compiler, charset: Charset, out: Logger) =
    (for (coffee <- (sources ** "*.coffee").get;
          js <- javascript(sources, coffee, target)
      if (coffee newerThan js)) yield {
        (coffee, js)
      }) match {
        case Nil =>
          out.info("No CoffeeScripts to compile")
          compiled(target)
        case xs =>
          out.info("Compiling %d CoffeeScripts to %s" format(xs.size, target))
          xs map compile(compiler, charset, out)
          out.debug("Compiled %s CoffeeScripts" format xs.size)
          compiled(target)
      }

  private def coffeeCleanTask =
    (streams, targetDirectory) map {
      (out, target) =>
        out.log.info("Cleaning generated JavaScript under " + target)
        IO.delete(target)
    }

  private def coffeeSourceGeneratorTask =
    (streams, sourceDirectory, targetDirectory, charset, bare) map {
      (out, sourceDir, targetDir, charset, bare) =>
        compileChanged(sourceDir, targetDir, compiler(bare), charset, out.log)
    }

  // move defaultExcludes to excludeFilter in unmanagedSources later
  private def coffeeSourcesTask =
    (sourceDirectory, filter, excludeFilter) map {
      (sourceDir, filt, excl) =>
         sourceDir.descendentsExcept(filt, excl).get
    }

  private def compiler(bare: Boolean) = if(bare) Compiler(true) else Compiler()

  def coffeeSettings: Seq[Setting[_]] = inConfig(Coffee)(Seq(
    sourceDirectory <<= (sourceDirectory in Compile) { _ / "coffee" },
    filter := "*.coffee",
    // change to (excludeFilter in Global) when dropping support of sbt 0.10.*
    excludeFilter := (".*"  - ".") || HiddenFileFilter,
    targetDirectory <<= (resourceManaged in Compile) { _ / "js" },
    sources <<= coffeeSourcesTask,
    bare := false,
    charset := Charset.forName("utf-8"),
    cleanFiles <+= targetDirectory.identity,
    clean <<= coffeeCleanTask,
    coffee <<= coffeeSourceGeneratorTask,
    resourceGenerators in Compile <+= coffee.identity
  )) ++ Seq(
    coffee <<= (coffee in Coffee).identity,
    watchSources <++= (sources in Coffee).identity
  )

}
