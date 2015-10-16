package xx.evalloop;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;

public class EvalLoopStarter {

  public static void main(String[] args) {

    String extension = "js";
    if (args.length > 0) {
      extension = args[0];
    }

    ScriptEngineManager mgr = new ScriptEngineManager();

    ScriptEngine engine = mgr.getEngineByExtension(extension);

    if (engine == null) {
      throw new IllegalArgumentException("No script engine found: " + extension);
    }

    EvalLoop loop = EvalLoop.getDefaultEvalLoop();

    loop.run();

  }

}
