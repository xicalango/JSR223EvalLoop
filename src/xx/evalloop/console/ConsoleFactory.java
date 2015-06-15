package xx.evalloop.console;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.Console;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;

public class ConsoleFactory {

  public static ConsoleAdapter getConsole() {
    final Console systemConsole = System.console();
    if (systemConsole != null) {
      return new JavaConsole(systemConsole);
    } else {
      final BufferedReader inReader = new BufferedReader(new InputStreamReader(System.in));
      final BufferedWriter outWriter = new BufferedWriter(new OutputStreamWriter(System.out));
      return new IOConsole(inReader, outWriter);
    }
  }

}
