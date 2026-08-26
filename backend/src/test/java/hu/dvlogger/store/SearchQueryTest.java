package hu.dvlogger.store;

import io.vertx.core.MultiMap;
import org.junit.jupiter.api.Test;
import java.time.Duration;
import java.time.Instant;
import static org.junit.jupiter.api.Assertions.*;

class SearchQueryTest {
  @Test void parsesAllParams() {
    MultiMap p = MultiMap.caseInsensitiveMultiMap().add("q","x").add("from","2026-01-01T00:00:00Z").add("to","2026-01-02T00:00:00Z")
        .add("source","s").add("tag","t").add("level","3").add("limit","5").add("before","abc");
    SearchQuery q = SearchQuery.fromParams(p);
    assertEquals("x", q.q()); assertEquals(Instant.parse("2026-01-01T00:00:00Z"), q.from());
    assertEquals("s", q.source()); assertEquals("t", q.tag()); assertEquals(3, q.level());
    assertEquals(5, q.limit()); assertEquals("abc", q.before());
  }
  @Test void lastMinutesOverridesFrom() {
    SearchQuery q = SearchQuery.fromParams(MultiMap.caseInsensitiveMultiMap().add("last","15").add("from","2020-01-01T00:00:00Z"));
    assertTrue(Duration.between(q.from(), Instant.now()).toMinutes() <= 15);
    assertNull(q.to());
  }
  @Test void defaultsAndBlanks() {
    SearchQuery q = SearchQuery.fromParams(MultiMap.caseInsensitiveMultiMap().add("q","  ").add("source",""));
    assertNull(q.q()); assertNull(q.source()); assertEquals(100, q.limit());
  }
  @Test void limitIsClampedToOneAndOneThousand() {
    assertEquals(1, SearchQuery.fromParams(MultiMap.caseInsensitiveMultiMap().add("limit","0")).limit());
    assertEquals(1, SearchQuery.fromParams(MultiMap.caseInsensitiveMultiMap().add("limit","-3")).limit());
    assertEquals(1000, SearchQuery.fromParams(MultiMap.caseInsensitiveMultiMap().add("limit","5000")).limit());
  }
  @Test void negativeLastThrows() {
    assertThrows(IllegalArgumentException.class,
        () -> SearchQuery.fromParams(MultiMap.caseInsensitiveMultiMap().add("last","-1")));
  }
}
