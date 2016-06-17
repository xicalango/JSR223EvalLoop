package xx.evalloop.console;

import java.io.Reader;
import java.io.Writer;
import java.util.Optional;

public interface ConsoleAdapter extends ConsoleOutput {

  Reader getReader();

  Writer getWriter();

  Optional<String> readLine();

  default Optional<String> readLine(String format, Object... pars) {
    printf(format, pars);
    return readLine();
  }

}
