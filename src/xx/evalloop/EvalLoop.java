package xx.evalloop;

import java.io.PrintWriter;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Stream;

import javax.script.Bindings;
import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineFactory;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;

import xx.evalloop.console.ConsoleAdapter;
import xx.evalloop.console.ConsoleFactory;
import xx.evalloop.console.ConsoleOutput;

public class EvalLoop implements Runnable {

  private static final Set<Method> objectMethods = getObjectMethodsSet();

  private static final int STREAM_LINES = 100;

  private final Map<String, Consumer<String>> commands = new HashMap<>();

  private final Map<Class<?>, BiConsumer<Object, ConsoleOutput>> specialPrinters = new HashMap<>();

  private final ScriptEngineManager manager;
  private ScriptEngine engine;
  private final Bindings bindings;

  private final ConsoleAdapter console;

  private final StreamPrinter streamPrinter = new StreamPrinter(STREAM_LINES);

  private boolean running = false;

  private int inputCount = 0;
  private int outputCount = 0;

  private String prompt;
  private List<Supplier<Object>> promptSuppliers = new ArrayList<>();
  private String outputPrefix = " ==> ";
  private String commandPrefix = "::";

  private Exception lastException;

  private final PrintWriter exceptionWriter;

  public static EvalLoop getDefaultEvalLoop() {
    ScriptEngineManager mgr = new ScriptEngineManager();
    ScriptEngine engine = mgr.getEngineByExtension("js");
    ConsoleAdapter console = ConsoleFactory.getConsole();
    return new EvalLoop(mgr, engine, console);
  }

  private static Set<Method> getObjectMethodsSet() {
    HashSet<Method> result = new HashSet<>();
    Collections.addAll(result, Object.class.getMethods());
    return result;
  }

  public EvalLoop(ScriptEngineManager manager, ScriptEngine engine, ConsoleAdapter console) {
    this.manager = manager;
    this.engine = engine;
    this.bindings = engine.createBindings();
    this.engine.setBindings(bindings, ScriptContext.ENGINE_SCOPE);
    this.console = console;
    exceptionWriter = new PrintWriter(console.getWriter());

    setPrompt("$l:$i> ");

    initCommands();
  }

  @SuppressWarnings("unchecked")
  public <T> void addPrinter(Class<T> forClass, BiConsumer<T, ConsoleOutput> printer) {
    specialPrinters.put(forClass, (BiConsumer<Object, ConsoleOutput>) printer);
  }

  public Bindings getBindings() {
    return bindings;
  }

  public void setOutputPrefix(String prefix) {
    outputPrefix = prefix;
  }

  public void setPrompt(String prompt) {

    promptSuppliers.clear();
    StringBuilder sb = new StringBuilder();

    char[] chars = prompt.toCharArray();

    for (int i = 0; i < chars.length; i++) {
      if (chars[i] == '$') {
        i++;
        switch (chars[i]) {
        case '$':
          sb.append('$');
          break;
        case 'i':
          sb.append("%03d");
          promptSuppliers.add(() -> inputCount);
          break;
        case 'e':
          sb.append("%s");
          promptSuppliers.add(() -> engine.getFactory().getEngineName());
          break;
        case 'l':
          sb.append("%s");
          promptSuppliers.add(() -> engine.getFactory().getLanguageName());
          break;
        case 'S':
          sb.append(" ");
          break;
        default:
          throw new IllegalArgumentException("Unknown format: " + chars[i]);
        }
      } else {
        sb.append(chars[i]);
      }
    }

    this.prompt = sb.toString();
  }

  private void initCommands() {
    commands.put("quit", p -> running = false);
    commands.put("stacktrace", p -> printLastException());
    commands.put("prompt", p -> setPrompt(p));
    commands.put("engines", p -> {
      for (ScriptEngineFactory factory : manager.getEngineFactories()) {
        console.printf("%s: %s %s\n", factory.getLanguageName(), factory.getEngineName(), factory.getExtensions().toString());
      }
    });
    commands.put("setEngine", p -> hotSwapEngine(p));
    commands.put("describe", p -> printDescription(p));
    commands.put("stream.limit", p -> streamPrinter.setLimit(Integer.valueOf(p)));
    commands.put("stream.toggleNumLines", p -> streamPrinter.toggleNumLines());
    commands.put("listCommands", p -> streamPrinter.print(commands.keySet().stream(), console));
  }

  private void printDescription(String p) {
    Object o = bindings.get(p);
    if (o == null) {
      console.printf("%s = null\n", p);
      return;
    }

    Class<?> clazz = o.getClass();

    console.printf("type(%s) = %s\n", p, clazz.getName());

    for (Method m : clazz.getMethods()) {
      if (objectMethods.contains(m) || !((m.getModifiers() & Modifier.PUBLIC) == Modifier.PUBLIC)) {
        continue;
      }
      console.printf("%s.%s[%d]\n", p, m.getName(), m.getParameterCount());
    }

  }

  private void hotSwapEngine(String extension) {
    ScriptEngine newEngine = manager.getEngineByExtension(extension);
    if (newEngine != null) {
      engine = newEngine;
      engine.setBindings(bindings, ScriptContext.ENGINE_SCOPE);
    } else {
      console.printf("No such engine: %s\n", extension);
    }
  }

  private void printLastException() {
    if (lastException == null) {
      console.printf("No stack trace available.\n");
      return;
    }

    lastException.printStackTrace(exceptionWriter);
  }

  private String readLine() {
    Object[] args = promptSuppliers.stream().map(s -> s.get()).toArray();
    String line = console.readLine(prompt, args);
    inputCount++;
    return line;
  }

  @Override
  public void run() {

    running = true;

    while (running) {
      String line = readLine();

      if (line == null) {
        running = false;
        continue;
      }

      try {
        if (preprocessLine(line) == true) {
          continue;
        }
      } catch (Exception e) {
        printException(e);
        lastException = e;
        continue;
      }

      try {
        Object result = engine.eval(line);

        printResult(result);

        bindings.put("_", result);
      } catch (ScriptException e) {
        printScriptException(e);
        lastException = e;
      } catch (Exception e) {
        printException(e);
        lastException = e;
      }

    }

  }

  private void printException(Exception e) {
    console.printf("Caught exception: %s\n", e);
  }

  private void printScriptException(ScriptException e) {
    console.printf("%s\n", e.getMessage());
  }

  private Optional<BiConsumer<Object, ConsoleOutput>> getSpecialPrinterFor(Class<?> resultClass) {
    return Optional.ofNullable(specialPrinters.get(resultClass));
  }

  private void printResult(Object result) {
    if (result == null) {
      return;
    }

    console.printf("%03d%s", outputCount, outputPrefix);

    if (Stream.class.isAssignableFrom(result.getClass())) {
      Stream<?> stream = (Stream<?>) result;
      streamPrinter.print(stream, console);
    } else {
      getSpecialPrinterFor(result.getClass()).orElse(EvalLoop::printResult).accept(result, console);
    }

    outputCount++;
  }

  private static void printResult(Object result, ConsoleOutput output) {
    output.printf("%s\n", String.valueOf(result));
  }

  private Optional<Consumer<String>> getInternalCommand(String line) {
    String command = line.substring(commandPrefix.length());

    if (!commands.containsKey(command)) {
      return Optional.empty();
    }

    return Optional.of(commands.get(command));
  }

  private boolean preprocessLine(String line) {
    if (!line.startsWith(commandPrefix)) {
      return false;
    }

    String command;
    String arg;

    int indexOf = line.indexOf(' ');
    if (indexOf < 0) {
      command = line;
      arg = null;
    } else {
      command = line.substring(0, indexOf);
      arg = line.substring(indexOf + 1);
    }

    return getInternalCommand(command).map(r -> {
      r.accept(arg);
      return true;
    }).orElseThrow(() -> new IllegalArgumentException("No such command: " + command));
  }
}
