package xx.evalloop;

import xx.evalloop.console.ConsoleAdapter;
import xx.evalloop.console.ConsoleFactory;
import xx.evalloop.console.ConsoleOutput;

import javax.script.*;
import java.io.PrintWriter;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Stream;

public class EvalLoop implements Runnable {

    private static final Set<Method> objectMethods = getObjectMethodsSet();

    private static final int STREAM_LINES = 100;

    private final Map<String, Consumer<String>> commands = new HashMap<>();

    private final Map<Class<?>, BiConsumer<Object, ConsoleOutput>> specialPrinters = new HashMap<>();

    private final ScriptEngineManager manager;
    private ScriptEngine engine;

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

    private final Bindings bindings;

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
        this.console = console;

        this.bindings = new CommonBindings();

        engine.setBindings(bindings, ScriptContext.ENGINE_SCOPE);

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
        commands.put("prompt", this::setPrompt);
        commands.put("engines", p -> printEngines());
        commands.put("setEngine", this::hotSwapEngine);
        commands.put("describe", this::printDescription);
        commands.put("stream.limit", p -> streamPrinter.setLimit(Integer.valueOf(p)));
        commands.put("stream.toggleNumLines", p -> streamPrinter.toggleNumLines());
        commands.put("help", p -> streamPrinter.print(commands.keySet().stream().sorted(), console));
        commands.put("bindings", p -> streamPrinter.print(getBindingsStream(), console));
        commands.put("newInstance", this::newInstance);
        commands.put("giveBindings", p -> getBindings().put("_", getBindings()));
        commands.put("getStaticMethod", this::getStaticMethod);
    }

    private void printEngines() {
        for (ScriptEngineFactory factory : manager.getEngineFactories()) {
            console.printf("%s: %s %s\n", factory.getLanguageName(), factory.getEngineName(), factory.getExtensions()
                    .toString());
        }
    }

    private void getStaticMethod(String p) {
        String[] args = p.split(" ");
        Class<?> className = stringToClass(args[0]);
        String methodName = args[1];
        Class<?>[] parameterClasses = Stream.of(args).skip(2).map(EvalLoop::stringToClass).toArray(Class[]::new);

        try {
            Method method = className.getMethod(methodName, parameterClasses);
            MethodInterface methodInterface = MethodInterface.forStaticMethod(method);
            getBindings().put("_", methodInterface);
            printResult(methodInterface);
        } catch (NoSuchMethodException e) {
            throw new IllegalArgumentException(p);
        }
    }

    private static Class<?> stringToClass(String className) {
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException e) {
            throw new IllegalArgumentException(className);
        }
    }

    private void newInstance(String className) {
        try {
            Class<?> clazz = Class.forName(className);

            Object newObject = clazz.newInstance();

            getBindings().put("_", newObject);
            printResult(newObject);
        } catch (ClassNotFoundException | InstantiationException | IllegalAccessException e) {
            throw new IllegalArgumentException(e);
        }
    }

    private Stream<String> getBindingsStream() {
        final Bindings bindings = getBindings();
        return bindings.keySet().stream().sorted().map(key -> key + ": " + getClassName(bindings.get(key)));
    }

    private static String getClassName(Object val) {
        if (val == null) {
            return "(null)";
        }
        return val.getClass().getName();
    }

    private void printDescription(String p) {
        Object o = getBindings().get(p);
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

    private Optional<String> readLine() {
        Object[] args = promptSuppliers.stream().map(Supplier::get).toArray();
        Optional<String> line = console.readLine(prompt, args);
        inputCount++;
        return line;
    }

    @Override
    public void run() {

        running = true;

        while (running) {
            Optional<String> optionalLine = readLine();

            if (!optionalLine.isPresent()) {
                running = false;
                continue;
            }

            String line = optionalLine.get();

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

                getBindings().put("_", result);
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
