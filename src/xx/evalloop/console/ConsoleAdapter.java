package xx.evalloop.console;

import java.io.Reader;
import java.io.Writer;

public interface ConsoleAdapter extends ConsoleOutput {

  public Reader getReader();

  public Writer getWriter();

  public String readLine();

  public default String readLine(String format, Object... pars) {
    printf(format, pars);
    return readLine();
  }

}
