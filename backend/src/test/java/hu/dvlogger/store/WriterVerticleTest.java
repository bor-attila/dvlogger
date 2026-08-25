package hu.dvlogger.store;

import hu.dvlogger.Config;
import hu.dvlogger.ingest.Ingest;
import hu.dvlogger.model.LogEntry;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.mongo.MongoClient;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

@Testcontainers @ExtendWith(VertxExtension.class)
class WriterVerticleTest {
  @Container static MongoDBContainer mongo = new MongoDBContainer("mongo:7");

  /** In-memory stand-in: fails the first N insert calls. */
  static class FakeIndex extends ManticoreIndex {
    final List<LogEntry> got = new ArrayList<>(); final AtomicInteger failLeft;
    FakeIndex(Vertx v, int failFirst) { super(v, "localhost", 1); failLeft = new AtomicInteger(failFirst); }
    @Override public Future<Void> insertMany(List<LogEntry> es) {
      if (failLeft.getAndDecrement() > 0) return Future.failedFuture("down");
      got.addAll(es); return Future.succeededFuture();
    }
  }

  /** Polls `check` until it stops throwing, then runs `onDone`; fails the test after timeoutMs. */
  private static void pollUntil(Vertx vertx, VertxTestContext ctx, long timeoutMs, Runnable check, Runnable onDone) {
    attempt(vertx, ctx, System.currentTimeMillis() + timeoutMs, check, onDone);
  }
  private static void attempt(Vertx vertx, VertxTestContext ctx, long deadline, Runnable check, Runnable onDone) {
    vertx.setTimer(50, t -> {
      try {
        check.run();
        onDone.run();
      } catch (Throwable e) {
        if (System.currentTimeMillis() > deadline) ctx.failNow(e);
        else attempt(vertx, ctx, deadline, check, onDone);
      }
    });
  }

  @Test void batchesToMongoArchiveAndIndexWithRetry(Vertx vertx, VertxTestContext ctx) {
    Config cfg = Config.fromEnv(Map.of("AUTH_USER","u","AUTH_PASSWORD","p","ARCHIVE_ENABLED","true","BATCH_SIZE","2","BATCH_MS","50"));
    MongoClient client = MongoClient.create(vertx, new JsonObject().put("connection_string", mongo.getConnectionString()).put("db_name", "w"));
    MongoStore ms = new MongoStore(client, 14); ArchiveStore as = new ArchiveStore(client);
    FakeIndex ix = new FakeIndex(vertx, 1); Stats stats = new Stats();
    client.removeDocuments(MongoStore.COLL, new JsonObject())
      .compose(v -> client.removeDocuments(ArchiveStore.COLL, new JsonObject()))
      .compose(v -> ms.init()).compose(v -> as.init())
      .compose(v -> vertx.deployVerticle(new WriterVerticle(cfg, ms, as, ix, stats)))
      .onComplete(ctx.succeeding(id -> {
        for (int i = 0; i < 3; i++)
          vertx.eventBus().send(Ingest.ADDRESS, LogEntry.of(Instant.now(), "s", List.of(), null, "m" + i, "h", new JsonObject()).toMongo());
        pollUntil(vertx, ctx, 5000, () -> {
          assertEquals(3, stats.written.get());
          assertEquals(3, ix.got.size());
          assertEquals(1, stats.indexFailed.get());
          assertEquals(0, stats.reindexQueue.get());
        }, () -> client.count(MongoStore.COLL, new JsonObject())
          .compose(n -> { ctx.verify(() -> assertEquals(3L, n)); return client.count(ArchiveStore.COLL, new JsonObject()); })
          .onComplete(ctx.succeeding(n -> ctx.verify(() -> {
            assertEquals(3L, n);
            ctx.completeNow();
          }))));
      }));
  }
}
