package coffeescript

import org.mozilla.javascript.{Context, Function, JavaScriptException, NativeObject}
import java.io.InputStreamReader
import java.nio.charset.Charset

object Compiler {
  val utf8 = Charset.forName("utf-8")
}

/**
 * A Scala / Rhino Coffeescript compiler.
 * @author daggerrz
 * @author doug (to a lesser degree)
 */
case class Compiler(bare: Boolean = false) {
  import Compiler._

  /**
   * Compiles a string of Coffeescript code to Javascript.
   *
   * @param code the Coffeescript source code
   * @return Either a compilation error description or the compiled Javascript code
   */
  def compile(code: String): Either[String, String] = withContext { ctx =>
    val scope = ctx.initStandardObjects()
    ctx.evaluateReader(scope,
      new InputStreamReader(getClass().getResourceAsStream("/coffee-script.js"), utf8),
     "coffee-script.js", 1, null
    )
    val coffee = scope.get("CoffeeScript", scope).asInstanceOf[NativeObject]
    val compileFunc = coffee.get("compile", scope).asInstanceOf[Function]
    val opts = ctx.evaluateString(scope, "({bare: %b});".format(bare), null, 1, null)
    try {
      Right(compileFunc.call(ctx, scope, coffee, Array(code, opts)).asInstanceOf[String])
    } catch {
      case e : JavaScriptException =>
        Left(e.getValue.toString)
    }
  }

  private def withContext[T](f: Context => T): T = {
    val ctx = Context.enter()
    try {
      ctx.setOptimizationLevel(-1) // Do not compile to byte code (max 64kb methods)
      f(ctx)
    } catch {
      case e =>
        throw e
    } finally {
      Context.exit()
    }
  }
}
