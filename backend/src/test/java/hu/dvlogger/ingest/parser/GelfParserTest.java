package hu.dvlogger.ingest.parser;

import hu.dvlogger.model.LogEntry;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class GelfParserTest {
  GelfParser p = new GelfParser();
  @Test void standardGelf() {
    LogEntry e = p.parse("{\"version\":\"1.1\",\"host\":\"web1\",\"short_message\":\"boom\",\"full_message\":\"stack\",\"timestamp\":1756116000.5,\"level\":3,\"_tags\":\"web,prod\",\"_user_id\":42}", "ip");
    assertEquals("boom", e.message());
    assertEquals("web1", e.source());
    assertEquals("web1", e.host());
    assertEquals(3, e.level());
    assertEquals(List.of("web","prod"), e.tags());
    assertEquals(Instant.ofEpochMilli(1756116000500L), e.ts());
    assertEquals(42, e.raw().getInteger("_user_id"));
    assertEquals("stack", e.raw().getString("full_message"));
  }
  @Test void sourceOverride() {
    LogEntry e = p.parse("{\"version\":\"1.1\",\"host\":\"web1\",\"short_message\":\"x\",\"_source\":\"billing\",\"_tags\":[\"t\"]}", "ip");
    assertEquals("billing", e.source());
    assertEquals(List.of("t"), e.tags());
    assertNull(e.level());
  }
  @Test void invalidIsUnparsed() {
    assertEquals(List.of("_unparsed"), p.parse("nope", "ip").tags());
  }
}
