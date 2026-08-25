package hu.dvlogger.ingest.parser;

import hu.dvlogger.model.LogEntry;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class JsonParserTest {
  JsonParser p = new JsonParser();
  @Test void fullObject() {
    LogEntry e = p.parse("{\"message\":\"hi\",\"source\":\"api\",\"tags\":[\"a\",\"b\"],\"level\":\"error\",\"ts\":\"2026-08-25T10:00:00Z\",\"host\":\"h1\",\"extra\":1}", "ip");
    assertEquals("hi", e.message());
    assertEquals("api", e.source());
    assertEquals(List.of("a","b"), e.tags());
    assertEquals(3, e.level());
    assertEquals(Instant.parse("2026-08-25T10:00:00Z"), e.ts());
    assertEquals("h1", e.host());
    assertEquals(1, e.raw().getInteger("extra"));
  }
  @Test void aliasesAndEpoch() {
    LogEntry e = p.parse("{\"msg\":\"m\",\"app\":\"svc\",\"tags\":\"x, y\",\"level\":4,\"timestamp\":1756116000}", "ip");
    assertEquals("m", e.message());
    assertEquals("svc", e.source());
    assertEquals(List.of("x","y"), e.tags());
    assertEquals(4, e.level());
    assertEquals(Instant.ofEpochSecond(1756116000), e.ts());
    assertEquals("ip", e.host());
  }
  @Test void missingFieldsUseDefaults() {
    LogEntry e = p.parse("{\"foo\":\"bar\"}", "ip");
    assertEquals("{\"foo\":\"bar\"}", e.message());
    assertEquals("ip", e.source());
    assertNull(e.level());
  }
  @Test void invalidJsonIsUnparsed() {
    LogEntry e = p.parse("{not json", "ip");
    assertEquals(List.of("_unparsed"), e.tags());
    assertEquals("{not json", e.message());
  }
}
