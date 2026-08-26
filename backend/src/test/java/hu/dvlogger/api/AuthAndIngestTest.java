package hu.dvlogger.api;

import hu.dvlogger.Config;
import hu.dvlogger.ingest.Ingest;
import hu.dvlogger.ingest.parser.Parsers;
import hu.dvlogger.store.Stats;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientSession;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(VertxExtension.class)
class AuthAndIngestTest {
  Config cfg = Config.fromEnv(Map.of("AUTH_USER","admin","AUTH_PASSWORD","secret","HTTP_PORT","18081","INGEST_TOKEN","tok"));

  @Test void loginFlow(Vertx vertx, VertxTestContext ctx) {
    WebClientSession c = WebClientSession.create(WebClient.create(vertx));
    vertx.deployVerticle(new ApiVerticle(cfg, null, null, null, new Stats(), Parsers.forConfig(cfg)))
      .compose(id -> c.get(18081, "localhost", "/api/me").send())
      .compose(r -> { ctx.verify(() -> assertEquals(401, r.statusCode()));
        return c.post(18081, "localhost", "/api/login").sendJsonObject(new JsonObject().put("user","admin").put("password","nope")); })
      .compose(r -> { ctx.verify(() -> assertEquals(401, r.statusCode()));
        return c.post(18081, "localhost", "/api/login").sendJsonObject(new JsonObject().put("user","admin").put("password","secret")); })
      .compose(r -> { ctx.verify(() -> assertEquals(204, r.statusCode()));
        return c.get(18081, "localhost", "/api/me").send(); })
      .compose(r -> { ctx.verify(() -> assertEquals("admin", r.bodyAsJsonObject().getString("user")));
        return c.post(18081, "localhost", "/api/logout").send(); })
      .compose(r -> c.get(18081, "localhost", "/api/me").send())
      .onComplete(ctx.succeeding(r -> ctx.verify(() -> { assertEquals(401, r.statusCode()); ctx.completeNow(); })));
  }

  @Test void malformedLoginBodyIs401(Vertx vertx, VertxTestContext ctx) {
    WebClient c = WebClient.create(vertx);
    vertx.deployVerticle(new ApiVerticle(cfg, null, null, null, new Stats(), Parsers.forConfig(cfg)))
      .compose(id -> c.post(18081, "localhost", "/api/login").putHeader("content-type", "application/json")
          .sendBuffer(io.vertx.core.buffer.Buffer.buffer("not json{")))
      .onComplete(ctx.succeeding(r -> ctx.verify(() -> { assertEquals(401, r.statusCode()); ctx.completeNow(); })));
  }

  @Test void ingestArrayAndLines(Vertx vertx, VertxTestContext ctx) {
    AtomicInteger n = new AtomicInteger();
    vertx.eventBus().consumer(Ingest.ADDRESS, m -> n.incrementAndGet());
    WebClient c = WebClient.create(vertx);
    vertx.deployVerticle(new ApiVerticle(cfg, null, null, null, new Stats(), Parsers.forConfig(cfg)))
      .compose(id -> c.post(18081, "localhost", "/api/ingest").sendBuffer(io.vertx.core.buffer.Buffer.buffer("x")))
      .compose(r -> { ctx.verify(() -> assertEquals(401, r.statusCode()));
        return c.post(18081, "localhost", "/api/ingest").putHeader("X-Ingest-Token","tok")
          .sendJson(new JsonArray().add(new JsonObject().put("message","a")).add(new JsonObject().put("message","b"))); })
      .compose(r -> { ctx.verify(() -> {
          assertEquals(2, r.bodyAsJsonObject().getInteger("accepted"));
          assertNull(r.getHeader("Set-Cookie"));
        });
        return c.post(18081, "localhost", "/api/ingest").putHeader("X-Ingest-Token","tok")
          .sendBuffer(io.vertx.core.buffer.Buffer.buffer("app [] l1\napp [] l2\n\napp [] l3")); })
      .onComplete(ctx.succeeding(r -> ctx.verify(() -> {
        assertEquals(3, r.bodyAsJsonObject().getInteger("accepted"));
        assertEquals(5, n.get());
        ctx.completeNow();
      })));
  }

  @Test void ingestArrayWithNonObjectElements(Vertx vertx, VertxTestContext ctx) {
    AtomicInteger n = new AtomicInteger();
    vertx.eventBus().consumer(Ingest.ADDRESS, m -> n.incrementAndGet());
    WebClient c = WebClient.create(vertx);
    vertx.deployVerticle(new ApiVerticle(cfg, null, null, null, new Stats(), Parsers.forConfig(cfg)))
      .compose(id -> c.post(18081, "localhost", "/api/ingest").putHeader("X-Ingest-Token","tok")
          .sendJson(new JsonArray().add(new JsonObject().put("message","a")).add("plain line").add(42)))
      .onComplete(ctx.succeeding(r -> ctx.verify(() -> {
        assertEquals(3, r.bodyAsJsonObject().getInteger("accepted"));
        assertEquals(3, n.get());
        ctx.completeNow();
      })));
  }

  @Test void ingestEmptyBodyAcceptsZero(Vertx vertx, VertxTestContext ctx) {
    WebClient c = WebClient.create(vertx);
    vertx.deployVerticle(new ApiVerticle(cfg, null, null, null, new Stats(), Parsers.forConfig(cfg)))
      .compose(id -> c.post(18081, "localhost", "/api/ingest").putHeader("X-Ingest-Token","tok")
          .sendBuffer(io.vertx.core.buffer.Buffer.buffer("")))
      .onComplete(ctx.succeeding(r -> ctx.verify(() -> {
        assertEquals(0, r.bodyAsJsonObject().getInteger("accepted"));
        ctx.completeNow();
      })));
  }
}
