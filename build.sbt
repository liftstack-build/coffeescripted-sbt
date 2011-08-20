sbtPlugin := true

name := "coffeescripted-sbt"

organization := "me.lessis"

posterousNotesVersion := "0.1.4"

version <<= (posterousNotesVersion, sbtVersion) ("%s-%s-SNAPSHOT" format(_,_))

libraryDependencies += "rhino" % "js" % "1.7R2"

publishTo :=  Some(Resolver.file("lessis repo", new java.io.File("/var/www/repo")))
