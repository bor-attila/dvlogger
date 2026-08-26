package hu.borat.dvlogger.model;

import io.vertx.core.json.JsonObject;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class LogEntryTest {
  @Test void mongoRoundTrip() {
    LogEntry e = new LogEntry("64f1a2b3c4d5e6f7a8b9c0d1", Instant.parse("2026-08-25T10:00:00Z"),
        "app1", List.of("web","prod"), 3, "hello", "host1", new JsonObject().put("k","v"));
    JsonObject m = e.toMongo();
    assertEquals("64f1a2b3c4d5e6f7a8b9c0d1", m.getJsonObject("_id").getString("$oid"));
    assertEquals("2026-08-25T10:00:00Z", m.getJsonObject("ts").getString("$date"));
    LogEntry back = LogEntry.fromMongo(m);
    assertEquals(e, back);
  }
  @Test void newEntryGetsGeneratedId() {
    LogEntry e = LogEntry.of(Instant.now(), "s", List.of(), null, "m", "h", new JsonObject());
    assertEquals(24, e.id().length());
  }
  @Test void unparsedKeepsRaw() {
    LogEntry e = LogEntry.unparsed("garbage", "10.0.0.5");
    assertEquals("garbage", e.message());
    assertEquals("10.0.0.5", e.source());
    assertEquals(List.of("_unparsed"), e.tags());
    assertNull(e.level());
  }
  @Test void apiJsonUsesPlainFields() {
    LogEntry e = LogEntry.of(Instant.parse("2026-08-25T10:00:00Z"), "s", List.of("t"), 6, "m", "h", new JsonObject());
    JsonObject a = e.toApi();
    assertEquals(e.id(), a.getString("id"));
    assertEquals("2026-08-25T10:00:00Z", a.getString("ts"));
    assertEquals(6, a.getInteger("level"));
  }
  @Test void consecutiveIdsAreLexicographicallyOrdered() {
    String id1 = LogEntry.newId();
    String id2 = LogEntry.newId();
    assertTrue(id2.compareTo(id1) > 0, "Second id should be lexicographically greater than first");
  }
}
