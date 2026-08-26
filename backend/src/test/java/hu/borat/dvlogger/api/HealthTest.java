package hu.borat.dvlogger.api;

import hu.borat.dvlogger.Config;
import hu.borat.dvlogger.store.Stats;
import io.vertx.core.Vertx;
import io.vertx.ext.web.client.WebClient;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(VertxExtension.class)
class HealthTest {
  @Test void healthReturnsOk(Vertx vertx, VertxTestContext ctx) {
    Config cfg = Config.fromEnv(Map.of("AUTH_USER","u","AUTH_PASSWORD","p","HTTP_PORT","18080","ARCHIVE_ENABLED","true"));
    vertx.deployVerticle(new ApiVerticle(cfg, null, null, null, new Stats(), null))
      .compose(id -> WebClient.create(vertx).get(18080, "localhost", "/api/health").send())
      .onComplete(ctx.succeeding(resp -> ctx.verify(() -> {
        assertEquals(200, resp.statusCode());
        assertEquals("ok", resp.bodyAsJsonObject().getString("status"));
        assertTrue(resp.bodyAsJsonObject().getBoolean("archiveEnabled"));
        assertTrue(resp.bodyAsJsonObject().getBoolean("footerText"));
        assertTrue(resp.bodyAsJsonObject().containsKey("stats"));
        ctx.completeNow();
      })));
  }
}
