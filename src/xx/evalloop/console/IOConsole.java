package xx.evalloop.console;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOError;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.util.Optional;

public final class IOConsole implements ConsoleAdapter {

  private final BufferedReader reader;
  private final BufferedWriter writer;

  public IOConsole(BufferedReader reader, BufferedWriter writer) {
    this.reader = reader;
    this.writer = writer;
  }

  @Override
  public Reader getReader() {
    return reader;
  }

  @Override
  public Writer getWriter() {
    return writer;
  }

  @Override
  public void printf(String format, Object... pars) {
    try {
      writer.write(String.format(format, pars));
      writer.flush();
    } catch (IOException e) {
      throw new IOError(e);
    }
  }

  @Override
  public Optional<String> readLine() {
    try {
      return Optional.ofNullable(reader.readLine()).map(String::trim);
    } catch (IOException e) {
      throw new IOError(e);
    }
  }

}
