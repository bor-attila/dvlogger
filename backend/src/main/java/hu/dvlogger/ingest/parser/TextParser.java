package hu.dvlogger.ingest.parser;

import hu.dvlogger.model.LogEntry;
import io.vertx.core.json.JsonObject;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TextParser implements LogParser {
  private static final Pattern DEFAULT = Pattern.compile("^(?<source>\\S+) \\[(?<tags>[^\\]]*)\\] ?(?<message>.*)$", Pattern.DOTALL);
  private final Pattern pattern;

  public TextParser(String patternOrNull) {
    this.pattern = patternOrNull == null ? DEFAULT : Pattern.compile(patternOrNull, Pattern.DOTALL);
  }

  @Override public LogEntry parse(String raw, String remoteIp) {
    Matcher m = pattern.matcher(raw);
    String source = remoteIp, message = raw;
    List<String> tags = List.of();
    if (m.matches()) {
      source = group(m, "source", remoteIp);
      message = group(m, "message", raw);
      tags = splitTags(group(m, "tags", ""));
    }
    return LogEntry.of(Instant.now(), source, tags, levelOf(message), message, remoteIp,
        new JsonObject().put("raw", raw));
  }

  private static String group(Matcher m, String name, String def) {
    try { String v = m.group(name); return v == null ? def : v; } catch (IllegalArgumentException e) { return def; }
  }
  static List<String> splitTags(String s) {
    return Arrays.stream(s.split(",")).map(String::trim).filter(t -> !t.isEmpty()).toList();
  }
  /** Syslog severity heuristic: ERROR→3, WARN→4, INFO→6, DEBUG→7, else null. */
  static Integer levelOf(String msg) {
    String u = msg.toUpperCase();
    if (u.contains("ERROR") || u.contains("FATAL")) return 3;
    if (u.contains("WARN")) return 4;
    if (u.contains("INFO")) return 6;
    if (u.contains("DEBUG") || u.contains("TRACE")) return 7;
    return null;
  }
}
