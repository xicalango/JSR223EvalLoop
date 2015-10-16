package xx.evalloop.console;

import java.io.Console;
import java.io.Reader;
import java.io.Writer;
import java.util.Optional;

public final class JavaConsole implements ConsoleAdapter {

    private final Console console;

    public JavaConsole(Console console) {
        this.console = console;
    }

    @Override
    public void printf(String format, Object... pars) {
        console.printf(format, pars);
    }

    @Override
    public Optional<String> readLine() {
        return Optional.ofNullable(console.readLine());
    }

    @Override
    public Optional<String> readLine(String format, Object... pars) {
        return Optional.ofNullable(console.readLine(format, pars));
    }

    @Override
    public Reader getReader() {
        return console.reader();
    }

    @Override
    public Writer getWriter() {
        return console.writer();
    }

    ;

}
