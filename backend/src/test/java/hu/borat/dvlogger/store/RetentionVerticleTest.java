package hu.borat.dvlogger.store;

import hu.borat.dvlogger.Config;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(VertxExtension.class)
class RetentionVerticleTest {
  @Test void deletesOlderThanRetention(Vertx vertx, VertxTestContext ctx) {
    AtomicReference<Instant> seen = new AtomicReference<>();
    ManticoreIndex ix = new ManticoreIndex(vertx, "localhost", 1) {
      @Override public Future<Void> deleteBefore(Instant c) { seen.set(c); return Future.succeededFuture(); }
    };
    Config cfg = Config.fromEnv(Map.of("AUTH_USER","u","AUTH_PASSWORD","p","RETENTION_DAYS","3"));
    vertx.deployVerticle(new RetentionVerticle(cfg, ix)).onComplete(ctx.succeeding(id -> ctx.verify(() -> {
      Duration age = Duration.between(seen.get(), Instant.now());
      assertTrue(age.toHours() >= 71 && age.toHours() <= 73);
      ctx.completeNow();
    })));
  }
}
