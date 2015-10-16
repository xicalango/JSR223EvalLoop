package xx.evalloop.console;

import java.io.Reader;
import java.io.Writer;
import java.util.Optional;

public interface ConsoleAdapter extends ConsoleOutput {

  public Reader getReader();

  public Writer getWriter();

  public Optional<String> readLine();

  public default Optional<String> readLine(String format, Object... pars) {
    printf(format, pars);
    return readLine();
  }

}
