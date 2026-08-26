package hu.dvlogger;

import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class ConfigTest {
  @Test void defaultsApplyWhenEnvEmpty() {
    Config c = Config.fromEnv(Map.of("AUTH_USER", "admin", "AUTH_PASSWORD", "pw"));
    assertEquals("admin", c.authUser());
    assertFalse(c.archiveEnabled());
    assertEquals("auto", c.logFormat());
    assertEquals(14, c.retentionDays());
    assertEquals("mongodb://mongo:27017/dvlogger", c.mongoUrl());
    assertEquals("manticore", c.manticoreHost());
    assertEquals(9306, c.manticorePort());
    assertEquals(8080, c.httpPort());
    assertEquals(11222, c.ingestPort());
    assertNull(c.ingestToken());
    assertEquals(500, c.batchSize());
    assertEquals(200, c.batchMs());
    assertFalse(c.reindexOnStart());
  }
  @Test void envOverrides() {
    Config c = Config.fromEnv(Map.of("AUTH_USER","u","AUTH_PASSWORD","p","ARCHIVE_ENABLED","true",
        "LOG_FORMAT","gelf","RETENTION_DAYS","3","INGEST_TOKEN","t","BATCH_SIZE","10"));
    assertTrue(c.archiveEnabled());
    assertEquals("gelf", c.logFormat());
    assertEquals(3, c.retentionDays());
    assertEquals("t", c.ingestToken());
    assertEquals(10, c.batchSize());
  }
  @Test void missingAuthThrows() {
    assertThrows(IllegalStateException.class, () -> Config.fromEnv(Map.of()));
  }
  @Test void invalidFormatThrows() {
    assertThrows(IllegalStateException.class,
        () -> Config.fromEnv(Map.of("AUTH_USER","u","AUTH_PASSWORD","p","LOG_FORMAT","xml")));
  }
  @Test void blankTextPatternIsNull() {
    Config c = Config.fromEnv(Map.of("AUTH_USER","u","AUTH_PASSWORD","p","LOG_TEXT_PATTERN","  "));
    assertNull(c.logTextPattern());
  }
}
