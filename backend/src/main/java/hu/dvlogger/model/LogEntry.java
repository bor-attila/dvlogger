package hu.dvlogger.model;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

public record LogEntry(String id, Instant ts, String source, List<String> tags, Integer level,
                       String message, String host, JsonObject raw) {

  private static final SecureRandom RND = new SecureRandom();
  private static final int PROCESS = RND.nextInt(0xFFFFFF);
  private static final AtomicLong COUNTER = new AtomicLong(new SecureRandom().nextLong() & 0xFFFFFFFFL);

  /** 24-hex ObjectId-compatible id: 8 hex seconds, 6 hex process, 10 hex counter. */
  public static String newId() {
    long secs = Instant.now().getEpochSecond();
    long c = COUNTER.incrementAndGet() & 0xFFFFFFFFFFL;
    return String.format("%08x%06x%010x", secs, PROCESS, c);
  }

  public static LogEntry of(Instant ts, String source, List<String> tags, Integer level,
                            String message, String host, JsonObject raw) {
    return new LogEntry(newId(), ts, source, List.copyOf(tags), level, message, host, raw);
  }

  public static LogEntry unparsed(String raw, String remoteIp) {
    return of(Instant.now(), remoteIp, List.of("_unparsed"), null, raw, remoteIp, new JsonObject().put("raw", raw));
  }

  public JsonObject toMongo() {
    return new JsonObject()
        .put("_id", new JsonObject().put("$oid", id))
        .put("ts", new JsonObject().put("$date", ts.toString()))
        .put("source", source).put("tags", new JsonArray(tags)).put("level", level)
        .put("message", message).put("host", host).put("raw", raw);
  }

  public static LogEntry fromMongo(JsonObject m) {
    return new LogEntry(m.getJsonObject("_id").getString("$oid"),
        Instant.parse(m.getJsonObject("ts").getString("$date")),
        m.getString("source"), m.getJsonArray("tags", new JsonArray()).stream().map(Object::toString).toList(),
        m.getInteger("level"), m.getString("message"), m.getString("host"),
        m.getJsonObject("raw", new JsonObject()));
  }

  public JsonObject toApi() {
    return new JsonObject().put("id", id).put("ts", ts.toString()).put("source", source)
        .put("tags", new JsonArray(tags)).put("level", level).put("message", message)
        .put("host", host).put("raw", raw);
  }
}
