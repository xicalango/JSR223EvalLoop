package xx.evalloop.console;

import java.io.Console;
import java.io.Reader;
import java.io.Writer;

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
  public String readLine() {
    return console.readLine();
  }

  @Override
  public String readLine(String format, Object... pars) {
    return console.readLine(format, pars);
  }

  @Override
  public Reader getReader() {
    return console.reader();
  }

  @Override
  public Writer getWriter() {
    return console.writer();
  };

}
