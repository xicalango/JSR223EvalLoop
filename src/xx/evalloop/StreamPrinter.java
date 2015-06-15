package xx.evalloop;

import java.util.stream.Stream;

import xx.evalloop.console.ConsoleAdapter;

public class StreamPrinter {

  private int lines;
  private int limit;

  private boolean numerateLines = false;

  public StreamPrinter(int limit) {
    this.limit = limit;
  }

  public void print(Stream<?> t, ConsoleAdapter u) {
    lines = 0;

    Stream<String> strings = t.map(Object::toString);

    if (numerateLines) {
      strings = strings.map(s -> lines + " " + s);
    }

    strings.peek(s -> {
      if (lines > 0 && lines % limit == 0) {
        u.readLine("Line %d. Press enter key to continue.", lines);
      }
      lines++;
    }).forEach(s -> u.printf("%s\n", s));

  }

  public void toggleNumLines() {
    numerateLines = !numerateLines;
  }

  public int getLimit() {
    return limit;
  }

  public void setLimit(int limit) {
    this.limit = limit;
  }

}
