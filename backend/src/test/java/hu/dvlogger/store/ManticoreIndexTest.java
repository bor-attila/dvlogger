package hu.dvlogger.store;

import hu.dvlogger.model.LogEntry;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import java.time.Instant;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

@Testcontainers @ExtendWith(VertxExtension.class)
class ManticoreIndexTest {
  @Container static GenericContainer<?> mc = new GenericContainer<>("manticoresearch/manticore:6.3.6")
      .withExposedPorts(9306).waitingFor(Wait.forListeningPort());

  static LogEntry e(String src, String msg, List<String> tags, int level, Instant ts) {
    return LogEntry.of(ts, src, tags, level, msg, "h", new JsonObject());
  }

  @Test void insertSearchDelete(Vertx vertx, VertxTestContext ctx) {
    ManticoreIndex ix = new ManticoreIndex(vertx, mc.getHost(), mc.getMappedPort(9306));
    Instant t0 = Instant.parse("2026-01-01T00:00:00Z");
    LogEntry a = e("web", "user login ok", List.of("auth"), 6, t0.plusSeconds(10));
    LogEntry b = e("web", "payment failed badly", List.of("pay"), 3, t0.plusSeconds(20));
    LogEntry c = e("db", "user login slow", List.of("auth"), 4, t0.plusSeconds(30));
    ix.init().compose(v -> ix.truncate()).compose(v -> ix.insertMany(List.of(a, b, c)))
      .compose(v -> ix.search(new SearchQuery("login", null, null, null, null, null, 10, null)))
      .compose(r -> { ctx.verify(() -> assertEquals(List.of(c.id(), a.id()), r));
        return ix.search(new SearchQuery(null, null, null, "web", "pay", null, 10, null)); })
      .compose(r -> { ctx.verify(() -> assertEquals(List.of(b.id()), r));
        return ix.search(new SearchQuery(null, t0.plusSeconds(15), t0.plusSeconds(25), null, null, null, 10, null)); })
      .compose(r -> { ctx.verify(() -> assertEquals(List.of(b.id()), r));
        return ix.search(new SearchQuery(null, null, null, null, null, 3, 10, null)); })
      .compose(r -> { ctx.verify(() -> assertEquals(List.of(b.id()), r));
        return ix.search(new SearchQuery(null, null, null, null, null, null, 1, c.id())); })
      .compose(r -> { ctx.verify(() -> assertEquals(List.of(b.id()), r));
        return ix.deleteBefore(t0.plusSeconds(25)); })
      .compose(v -> ix.search(new SearchQuery(null, null, null, null, null, null, 10, null)))
      .onComplete(ctx.succeeding(r -> ctx.verify(() -> { assertEquals(List.of(c.id()), r); ctx.completeNow(); })));
  }
}
