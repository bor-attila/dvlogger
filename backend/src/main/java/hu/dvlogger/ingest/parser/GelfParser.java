package hu.dvlogger.ingest.parser;

import hu.dvlogger.model.LogEntry;
import io.vertx.core.json.JsonObject;

public class GelfParser implements LogParser {
  public static boolean looksLikeGelf(JsonObject o) {
    return o.containsKey("version") && o.containsKey("short_message");
  }
  @Override public LogEntry parse(String raw, String remoteIp) {
    if (raw == null) raw = "";
    JsonObject o;
    try { o = new JsonObject(raw); } catch (Exception e) { return LogEntry.unparsed(raw, remoteIp); }
    if (!looksLikeGelf(o)) return LogEntry.unparsed(raw, remoteIp);
    String host = o.getString("host", remoteIp);
    String source = JsonParser.firstString(o, host, "_source");
    return LogEntry.of(JsonParser.tsOf(o.getValue("timestamp")), source, JsonParser.tagsOf(o.getValue("_tags")),
        JsonParser.levelOf(o.getValue("level")), o.getString("short_message"), host, o);
  }
}
