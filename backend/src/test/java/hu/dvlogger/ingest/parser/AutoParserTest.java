package hu.dvlogger.ingest.parser;

import hu.dvlogger.Config;
import hu.dvlogger.model.LogEntry;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class AutoParserTest {
  LogParser p = Parsers.forConfig(Config.fromEnv(Map.of("AUTH_USER","u","AUTH_PASSWORD","p")));
  @Test void detectsGelf() {
    LogEntry e = p.parse("{\"version\":\"1.1\",\"host\":\"h\",\"short_message\":\"g\"}", "ip");
    assertEquals("g", e.message()); assertEquals("h", e.source());
  }
  @Test void detectsJson() {
    LogEntry e = p.parse(" {\"message\":\"j\",\"source\":\"s\"}", "ip");
    assertEquals("j", e.message()); assertEquals("s", e.source());
  }
  @Test void fallsBackToText() {
    LogEntry e = p.parse("app [t] hello", "ip");
    assertEquals("app", e.source()); assertEquals(List.of("t"), e.tags());
  }
  @Test void factoryHonoursFixedFormat() {
    LogParser t = Parsers.forConfig(Config.fromEnv(Map.of("AUTH_USER","u","AUTH_PASSWORD","p","LOG_FORMAT","text")));
    LogEntry e = t.parse("{\"message\":\"j\"}", "ip");
    assertEquals("{\"message\":\"j\"}", e.message());
    assertEquals("ip", e.source());
  }
  @Test void nullRawDoesNotThrow() {
    LogEntry e = p.parse(null, "10.0.0.1");
    assertEquals("", e.message());
    assertEquals("10.0.0.1", e.source());
    assertEquals(List.of(), e.tags());
  }
  @Test void blankPatternEnvStillParsesTextPrefix() {
    LogParser parser = Parsers.forConfig(Config.fromEnv(Map.of("AUTH_USER","u","AUTH_PASSWORD","p","LOG_TEXT_PATTERN","")));
    LogEntry e = parser.parse("app1 [web,prod] hello from tcp", "1.2.3.4");
    assertEquals("app1", e.source());
    assertEquals(List.of("web","prod"), e.tags());
    assertEquals("hello from tcp", e.message());
  }
}
