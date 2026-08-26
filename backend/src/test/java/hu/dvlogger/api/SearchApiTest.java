package hu.dvlogger.api;

import hu.dvlogger.Config;
import hu.dvlogger.ingest.parser.Parsers;
import hu.dvlogger.model.LogEntry;
import hu.dvlogger.store.*;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.mongo.MongoClient;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientSession;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

@Testcontainers @ExtendWith(VertxExtension.class)
class SearchApiTest {
  @Container static MongoDBContainer mongo = new MongoDBContainer("mongo:7");

  @Test void liveAndArchiveSearch(Vertx vertx, VertxTestContext ctx) {
    Config cfg = Config.fromEnv(Map.of("AUTH_USER","a","AUTH_PASSWORD","p","HTTP_PORT","18082","ARCHIVE_ENABLED","true"));
    MongoClient client = MongoClient.create(vertx, new JsonObject().put("connection_string", mongo.getConnectionString()).put("db_name", "api"));
    MongoStore ms = new MongoStore(client, 14); ArchiveStore as = new ArchiveStore(client);
    LogEntry e1 = LogEntry.of(Instant.now(), "web", List.of("t1"), 6, "one", "h", new JsonObject());
    LogEntry e2 = LogEntry.of(Instant.now(), "db", List.of("t2"), 3, "two", "h", new JsonObject());
    ManticoreIndex fake = new ManticoreIndex(vertx, "localhost", 1) {
      @Override public Future<List<String>> search(SearchQuery q) { return Future.succeededFuture(List.of(e2.id(), e1.id())); }
    };
    WebClientSession c = WebClientSession.create(WebClient.create(vertx));
    ms.init().compose(v -> as.init()).compose(v -> ms.insertMany(List.of(e1, e2))).compose(v -> as.insertMany(List.of(e1, e2)))
      .compose(v -> vertx.deployVerticle(new ApiVerticle(cfg, ms, as, fake, new Stats(), Parsers.forConfig(cfg))))
      .compose(id -> c.get(18082, "localhost", "/api/logs").send())
      .compose(r -> { ctx.verify(() -> assertEquals(401, r.statusCode()));
        return c.post(18082, "localhost", "/api/login").sendJsonObject(new JsonObject().put("user","a").put("password","p")); })
      .compose(r -> c.get(18082, "localhost", "/api/logs").addQueryParam("limit","1").send())
      .compose(r -> { ctx.verify(() -> {
          JsonObject b = r.bodyAsJsonObject();
          assertEquals(1, b.getJsonArray("items").size());
          assertEquals("two", b.getJsonArray("items").getJsonObject(0).getString("message"));
          assertEquals(e2.id(), b.getString("next")); });
        return c.get(18082, "localhost", "/api/sources").send(); })
      .compose(r -> { ctx.verify(() -> assertEquals(List.of("db","web"), r.bodyAsJsonArray().getList()));
        return c.get(18082, "localhost", "/api/archive/logs").addQueryParam("source","web").send(); })
      .compose(r -> { ctx.verify(() -> assertEquals("one", r.bodyAsJsonObject().getJsonArray("items").getJsonObject(0).getString("message")));
        return c.get(18082, "localhost", "/api/archive/tags").send(); })
      .onComplete(ctx.succeeding(r -> ctx.verify(() -> { assertEquals(List.of("t1","t2"), r.bodyAsJsonArray().getList()); ctx.completeNow(); })));
  }
}
