package hu.dvlogger.ingest.parser;

import hu.dvlogger.model.LogEntry;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class TextParserTest {
  TextParser p = new TextParser(null);

  @Test void prefixWithSourceAndTags() {
    LogEntry e = p.parse("app1 [web,prod] hello world", "10.0.0.1");
    assertEquals("app1", e.source());
    assertEquals(List.of("web","prod"), e.tags());
    assertEquals("hello world", e.message());
    assertEquals("10.0.0.1", e.host());
  }
  @Test void prefixWithSourceOnly() {
    LogEntry e = p.parse("app1 [] hello", "10.0.0.1");
    assertEquals("app1", e.source());
    assertEquals(List.of(), e.tags());
    assertEquals("hello", e.message());
  }
  @Test void noPrefixFallsBackToIp() {
    LogEntry e = p.parse("just a message", "10.0.0.1");
    assertEquals("10.0.0.1", e.source());
    assertEquals(List.of(), e.tags());
    assertEquals("just a message", e.message());
  }
  @Test void levelHeuristicFromKeyword() {
    assertEquals(3, p.parse("app [] ERROR boom", "ip").level());
    assertEquals(4, p.parse("app [] something WARN", "ip").level());
    assertEquals(6, p.parse("app [] INFO ok", "ip").level());
    assertEquals(7, p.parse("app [] DEBUG x", "ip").level());
    assertNull(p.parse("app [] plain", "ip").level());
  }
  @Test void customPattern() {
    TextParser c = new TextParser("^(?<source>\\w+)\\|(?<tags>[\\w,]*)\\|(?<message>.*)$");
    LogEntry e = c.parse("svc|a,b|msg here", "ip");
    assertEquals("svc", e.source());
    assertEquals(List.of("a","b"), e.tags());
    assertEquals("msg here", e.message());
  }
  @Test void customPatternNoMatchFallsBack() {
    TextParser c = new TextParser("^(?<source>\\w+)\\|(?<message>.*)$");
    LogEntry e = c.parse("no pipe here", "ip");
    assertEquals("ip", e.source());
    assertEquals("no pipe here", e.message());
  }
}
