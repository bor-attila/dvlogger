package hu.dvlogger.ingest.parser;

import hu.dvlogger.model.LogEntry;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import java.time.Instant;
import java.util.List;

public class JsonParser implements LogParser {
  @Override public LogEntry parse(String raw, String remoteIp) {
    if (raw == null) raw = "";
    JsonObject o;
    try { o = new JsonObject(raw); } catch (Exception e) { return LogEntry.unparsed(raw, remoteIp); }
    String message = firstString(o, raw, "message", "msg");
    String source = firstString(o, remoteIp, "source", "app", "service");
    String host = firstString(o, remoteIp, "host");
    return LogEntry.of(tsOf(o.getValue("ts", o.getValue("timestamp", o.getValue("time")))),
        source, tagsOf(o.getValue("tags")), levelOf(o.getValue("level")), message, host, o);
  }

  static String firstString(JsonObject o, String def, String... keys) {
    for (String k : keys) { Object v = o.getValue(k); if (v != null) return v.toString(); }
    return def;
  }
  static List<String> tagsOf(Object v) {
    if (v instanceof JsonArray a) return a.stream().map(Object::toString).map(String::trim).filter(s -> !s.isEmpty()).toList();
    if (v instanceof String s) return TextParser.splitTags(s);
    return List.of();
  }
  static Integer levelOf(Object v) {
    if (v instanceof Number n) return n.intValue();
    if (v instanceof String s) {
      try { return Integer.parseInt(s.trim()); } catch (NumberFormatException ignored) { }
      return TextParser.levelOf(s);
    }
    return null;
  }
  static Instant tsOf(Object v) {
    try {
      if (v instanceof Number n) {
        double d = n.doubleValue();
        return d > 1e12 ? Instant.ofEpochMilli((long) d) : Instant.ofEpochMilli(Math.round(d * 1000));
      }
      if (v instanceof String s) return Instant.parse(s);
    } catch (Exception ignored) { }
    return Instant.now();
  }
}
