package hu.dvlogger.ingest.parser;

import hu.dvlogger.model.LogEntry;
import io.vertx.core.json.JsonObject;

public class AutoParser implements LogParser {
  private final TextParser text; private final JsonParser json = new JsonParser(); private final GelfParser gelf = new GelfParser();
  public AutoParser(TextParser text) { this.text = text; }

  @Override public LogEntry parse(String raw, String remoteIp) {
    String t = raw.stripLeading();
    if (t.startsWith("{")) {
      try {
        JsonObject o = new JsonObject(t);
        return GelfParser.looksLikeGelf(o) ? gelf.parse(t, remoteIp) : json.parse(t, remoteIp);
      } catch (Exception ignored) { /* not JSON after all */ }
    }
    return text.parse(raw, remoteIp);
  }
}
