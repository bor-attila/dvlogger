package hu.borat.dvlogger.store;

import hu.borat.dvlogger.Config;
import hu.borat.dvlogger.ingest.Ingest;
import hu.borat.dvlogger.model.LogEntry;
import io.vertx.core.Future;
import io.vertx.core.Promise;
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
import java.util.Collections;
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

  /** In-memory stand-in whose first insert never completes until the test completes it, and that
   * records the size of every batch it is handed. */
  static class BlockingMongo extends MongoStore {
    final List<Integer> batchSizes = Collections.synchronizedList(new ArrayList<>());
    final AtomicInteger inserted = new AtomicInteger();
    final Promise<Void> firstInsert = Promise.promise();
    private boolean blocked = false;
    BlockingMongo() { super(null, 14); }
    @Override public Future<Void> insertMany(List<LogEntry> entries) {
      batchSizes.add(entries.size());
      inserted.addAndGet(entries.size());
      if (!blocked) { blocked = true; return firstInsert.future(); }
      return Future.succeededFuture();
    }
  }

  /** In-memory stand-in that always succeeds immediately: keeps flush() fully synchronous so
   * batchSize=1 messages never bundle into a single batch while a prior flush is in flight. */
  static class FakeMongo extends MongoStore {
    FakeMongo() { super(null, 14); }
    @Override public Future<Void> insertMany(List<LogEntry> entries) { return Future.succeededFuture(); }
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

  /** Sends n messages in chunks of `chunk`, letting the event loop breathe between chunks so the
   * consumer's pause actually takes effect (a tight loop from the test thread can schedule
   * messages onto the writer's context faster than the writer's handler runs). */
  private static void sendChunked(Vertx vertx, int remaining, int chunk) {
    int k = Math.min(chunk, remaining);
    for (int i = 0; i < k; i++)
      vertx.eventBus().send(Ingest.ADDRESS, LogEntry.of(Instant.now(), "s", List.of(), null, "m", "h", new JsonObject()).toMongo());
    if (remaining - k > 0) vertx.setTimer(5, t -> sendChunked(vertx, remaining - k, chunk));
  }

  @Test void backpressurePausesConsumerAndCapsBatchSize(Vertx vertx, VertxTestContext ctx) {
    int batchSize = 10, n = batchSize * 25;
    Config cfg = Config.fromEnv(Map.of("AUTH_USER","u","AUTH_PASSWORD","p","BATCH_SIZE",String.valueOf(batchSize),"BATCH_MS","50"));
    BlockingMongo bm = new BlockingMongo();
    FakeIndex ix = new FakeIndex(vertx, 0);
    Stats stats = new Stats();
    WriterVerticle wv = new WriterVerticle(cfg, bm, null, ix, stats);
    vertx.deployVerticle(wv).onComplete(ctx.succeeding(id -> {
      sendChunked(vertx, n, batchSize);
      // Phase 1: the very first flush is stuck in Mongo, so the buffer fills until the high-water
      // mark pauses the consumer -- it must not keep growing with every message that arrives.
      pollUntil(vertx, ctx, 5000, () -> assertTrue(wv.isPaused(), "consumer never paused"), () -> {
        ctx.verify(() -> assertTrue(wv.bufferSize() <= wv.highWater() + batchSize,
            "buffer grew past the high-water mark: " + wv.bufferSize()));
        bm.firstInsert.complete();
        // Phase 2: everything drains, and no batch is ever bigger than batchSize.
        pollUntil(vertx, ctx, 10000, () -> assertEquals(n, bm.inserted.get()), () -> ctx.verify(() -> {
          assertEquals(n, stats.written.get());
          assertEquals(0, stats.overflowDropped.get(), "messages were discarded by the event bus");
          assertFalse(wv.isPaused(), "consumer never resumed");
          assertTrue(Collections.max(bm.batchSizes) <= batchSize, "batch sizes: " + bm.batchSizes);
          ctx.completeNow();
        }));
      });
    }));
  }

  @Test void undeployFlushesEverythingBuffered(Vertx vertx, VertxTestContext ctx) {
    int n = 45;
    // BATCH_MS is long enough that only the batchSize trigger and stop()'s drain can flush.
    Config cfg = Config.fromEnv(Map.of("AUTH_USER","u","AUTH_PASSWORD","p","BATCH_SIZE","10","BATCH_MS","60000"));
    MongoClient client = MongoClient.create(vertx, new JsonObject().put("connection_string", mongo.getConnectionString()).put("db_name", "wstop"));
    MongoStore ms = new MongoStore(client, 14);
    Stats stats = new Stats();
    client.removeDocuments(MongoStore.COLL, new JsonObject())
      .compose(v -> vertx.deployVerticle(new WriterVerticle(cfg, ms, null, new FakeIndex(vertx, 0), stats)))
      .onComplete(ctx.succeeding(id -> {
        for (int i = 0; i < n; i++)
          vertx.eventBus().send(Ingest.ADDRESS, LogEntry.of(Instant.now(), "s", List.of(), null, "m" + i, "h", new JsonObject()).toMongo());
        pollUntil(vertx, ctx, 5000, () -> assertEquals(n, stats.received.get()), () ->
          vertx.undeploy(id)
            .compose(v -> client.count(MongoStore.COLL, new JsonObject()))
            .onComplete(ctx.succeeding(count -> ctx.verify(() -> {
              assertEquals((long) n, count, "entries buffered at shutdown were lost");
              assertEquals(n, stats.written.get());
              ctx.completeNow();
            }))));
      }));
  }

  @Test void reindexQueueIsBounded(Vertx vertx, VertxTestContext ctx) {
    Config cfg = Config.fromEnv(Map.of("AUTH_USER","u","AUTH_PASSWORD","p","BATCH_SIZE","1","BATCH_MS","50"));
    FakeIndex ix = new FakeIndex(vertx, Integer.MAX_VALUE); // always fails
    Stats stats = new Stats();
    // Shrink MAX_REINDEX_BATCHES (200 in production) so the test doesn't need 205 messages.
    int max = 3;
    WriterVerticle wv = new WriterVerticle(cfg, new FakeMongo(), null, ix, stats);
    wv.maxReindexBatches = max;
    vertx.deployVerticle(wv).onComplete(ctx.succeeding(id -> {
      for (int i = 0; i < max + 5; i++)
        vertx.eventBus().send(Ingest.ADDRESS, LogEntry.of(Instant.now(), "s", List.of(), null, "m" + i, "h", new JsonObject()).toMongo());
      pollUntil(vertx, ctx, 5000, () -> {
        assertTrue(stats.indexDropped.get() >= 5);
        assertEquals(max, stats.reindexQueue.get());
      }, ctx::completeNow);
    }));
  }
}
