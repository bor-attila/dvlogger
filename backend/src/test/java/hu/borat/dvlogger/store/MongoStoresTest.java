package hu.borat.dvlogger.store;

import hu.borat.dvlogger.model.LogEntry;
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

  private static JsonObject ttlIndex(io.vertx.core.json.JsonArray indexes) {
    for (int i = 0; i < indexes.size(); i++) {
      JsonObject idx = indexes.getJsonObject(i);
      if ("ts_ttl".equals(idx.getString("name"))) return idx;
    }
    return null;
  }
  private static long ttlCount(io.vertx.core.json.JsonArray indexes) {
    return indexes.stream().map(o -> (JsonObject) o).filter(o -> "ts_ttl".equals(o.getString("name"))).count();
  }

  /** init() used to drop and rebuild the TTL index on every startup (minutes of index build on a
   * big collection); it must now leave an up-to-date index alone and use collMod to change it. */
  @Test void initIsIdempotentAndUpdatesTtlViaCollMod(VertxTestContext ctx) {
    MongoStore s14 = new MongoStore(client, 14), s3 = new MongoStore(client, 3);
    s14.init()
      .compose(v -> client.listIndexes(MongoStore.COLL))
      .compose(idx -> {
        ctx.verify(() -> {
          JsonObject ttl = ttlIndex(idx);
          assertNotNull(ttl, "ts_ttl index missing");
          assertEquals(14L * 86400L, ttl.getNumber("expireAfterSeconds").longValue());
          assertEquals(new JsonObject().put("ts", 1), ttl.getJsonObject("key"));
        });
        return s3.init();
      })
      .compose(v -> client.listIndexes(MongoStore.COLL))
      .compose(idx -> {
        ctx.verify(() -> {
          assertEquals(1L, ttlCount(idx), "TTL index duplicated");
          JsonObject ttl = ttlIndex(idx);
          assertEquals(259200L, ttl.getNumber("expireAfterSeconds").longValue());
          assertEquals(new JsonObject().put("ts", 1), ttl.getJsonObject("key"), "index key spec changed");
        });
        return s3.init();
      })
      .compose(v -> client.listIndexes(MongoStore.COLL))
      .compose(idx -> {
        ctx.verify(() -> {
          assertEquals(1L, ttlCount(idx));
          assertEquals(259200L, ttlIndex(idx).getNumber("expireAfterSeconds").longValue());
        });
        return s14.init(); // leave the shared collection on the default retention
      })
      .onComplete(ctx.succeedingThenComplete());
  }

  /** forEachBatch used to nest one compose per page, so the chain unwound synchronously at the end
   * and blew the stack on a big collection. 2000 single-document pages exercise that (each page is
   * a Mongo round trip, so this is the practical limit for test runtime). */
  @Test @io.vertx.junit5.Timeout(value = 120, timeUnit = java.util.concurrent.TimeUnit.SECONDS)
  void forEachBatchIteratesManyPagesWithoutStackOverflow(VertxTestContext ctx) {
    MongoStore s = new MongoStore(client, 14);
    int n = 2000;
    List<LogEntry> es = new java.util.ArrayList<>();
    for (int i = 0; i < n; i++) es.add(entry("s", "m" + i, List.of(), Instant.now()));
    java.util.concurrent.atomic.AtomicInteger seen = new java.util.concurrent.atomic.AtomicInteger();
    s.insertMany(es)
      .compose(v -> s.forEachBatch(1, batch -> { seen.addAndGet(batch.size()); return Future.succeededFuture(); }))
      .onComplete(ctx.succeeding(v -> ctx.verify(() -> { assertEquals(n, seen.get()); ctx.completeNow(); })));
  }

  @Test void archiveRegexWhenQuoted(VertxTestContext ctx) {
    ArchiveStore ar = new ArchiveStore(client);
    ar.init().compose(v -> ar.insertMany(List.of(entry("s","abcXYZdef",List.of(),Instant.now()))))
      .compose(v -> ar.search(new SearchQuery("\"cXY\"", null, null, null, null, null, 10, null)))
      .onComplete(ctx.succeeding(r -> ctx.verify(() -> { assertEquals(1, r.size()); ctx.completeNow(); })));
  }
}
