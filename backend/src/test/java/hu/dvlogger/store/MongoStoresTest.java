package hu.dvlogger.store;

import hu.dvlogger.model.LogEntry;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.mongo.MongoClient;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

@Testcontainers @ExtendWith(VertxExtension.class)
class MongoStoresTest {
  @Container static MongoDBContainer mongo = new MongoDBContainer("mongo:7");
  static MongoClient client;

  @BeforeAll static void setup(Vertx vertx) {
    client = MongoClient.create(vertx, new JsonObject().put("connection_string", mongo.getConnectionString()).put("db_name", "t"));
  }

  // Isolates each test: Mongo's BSON date type only has ms precision, and the tests share one
  // collection across the class, so leftover docs from another test could otherwise be matched.
  @BeforeEach void cleanCollections(VertxTestContext ctx) {
    client.removeDocuments(MongoStore.COLL, new JsonObject())
        .compose(v -> client.removeDocuments(ArchiveStore.COLL, new JsonObject()))
        .onComplete(ctx.succeedingThenComplete());
  }

  static LogEntry entry(String src, String msg, List<String> tags, Instant ts) {
    return LogEntry.of(ts.truncatedTo(ChronoUnit.MILLIS), src, tags, 6, msg, "h", new JsonObject());
  }

  @Test void insertAndFindByIdsKeepsOrder(VertxTestContext ctx) {
    MongoStore s = new MongoStore(client, 14);
    LogEntry a = entry("a","1",List.of(),Instant.now()), b = entry("b","2",List.of(),Instant.now());
    s.init().compose(v -> s.insertMany(List.of(a, b)))
      .compose(v -> s.findByIds(List.of(b.id(), a.id())))
      .onComplete(ctx.succeeding(list -> ctx.verify(() -> {
        assertEquals(List.of(b, a), list);
        ctx.completeNow();
      })));
  }

  @Test void insertManyIsIdempotentOnDuplicateIds(VertxTestContext ctx) {
    MongoStore s = new MongoStore(client, 14);
    List<LogEntry> es = List.of(entry("a","1",List.of(),Instant.now()), entry("b","2",List.of(),Instant.now()));
    s.insertMany(es)
      .compose(v -> s.insertMany(es))
      .compose(v -> client.count(MongoStore.COLL, new JsonObject()))
      .onComplete(ctx.succeeding(n -> ctx.verify(() -> { assertEquals(2L, n); ctx.completeNow(); })));
  }

  @Test void distinctSources(VertxTestContext ctx) {
    MongoStore s = new MongoStore(client, 14);
    s.insertMany(List.of(entry("x","1",List.of("t1"),Instant.now()), entry("y","2",List.of("t2"),Instant.now())))
      .compose(v -> s.distinct("source"))
      .onComplete(ctx.succeeding(l -> ctx.verify(() -> { assertTrue(l.containsAll(List.of("x","y"))); ctx.completeNow(); })));
  }

  @Test void archiveSearchFilters(VertxTestContext ctx) {
    ArchiveStore ar = new ArchiveStore(client);
    Instant t0 = Instant.parse("2026-01-01T00:00:00Z");
    List<LogEntry> es = List.of(
        entry("web","user login ok",List.of("auth"), t0.plusSeconds(10)),
        entry("web","payment failed",List.of("pay"), t0.plusSeconds(20)),
        entry("db","user login slow",List.of("auth"), t0.plusSeconds(30)));
    ar.init().compose(v -> ar.insertMany(es))
      .compose(v -> ar.search(new SearchQuery("login", null, null, "web", "auth", null, 10, null)))
      .compose(r -> { ctx.verify(() -> { assertEquals(1, r.size()); assertEquals("user login ok", r.get(0).message()); });
        return ar.search(new SearchQuery(null, t0.plusSeconds(15), null, null, null, null, 10, null)); })
      .compose(r -> { ctx.verify(() -> assertEquals(List.of("user login slow","payment failed"), r.stream().map(LogEntry::message).toList()));
        return ar.search(new SearchQuery(null, null, null, null, null, null, 1, null)); })
      .compose(r -> { ctx.verify(() -> assertEquals("user login slow", r.get(0).message()));
        return ar.search(new SearchQuery(null, null, null, null, null, null, 1, r.get(0).id())); })
      .onComplete(ctx.succeeding(r -> ctx.verify(() -> { assertEquals("payment failed", r.get(0).message()); ctx.completeNow(); })));
  }

  @Test void archiveRegexWhenQuoted(VertxTestContext ctx) {
    ArchiveStore ar = new ArchiveStore(client);
    ar.init().compose(v -> ar.insertMany(List.of(entry("s","abcXYZdef",List.of(),Instant.now()))))
      .compose(v -> ar.search(new SearchQuery("\"cXY\"", null, null, null, null, null, 10, null)))
      .onComplete(ctx.succeeding(r -> ctx.verify(() -> { assertEquals(1, r.size()); ctx.completeNow(); })));
  }
}
