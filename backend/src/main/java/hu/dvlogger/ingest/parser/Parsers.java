package hu.dvlogger.ingest.parser;

import hu.dvlogger.Config;

public final class Parsers {
  private Parsers() { }
  public static LogParser forConfig(Config cfg) {
    TextParser text = new TextParser(cfg.logTextPattern());
    return switch (cfg.logFormat()) {
      case "text" -> text;
      case "json" -> new JsonParser();
      case "gelf" -> new GelfParser();
      default -> new AutoParser(text);
    };
  }
}
