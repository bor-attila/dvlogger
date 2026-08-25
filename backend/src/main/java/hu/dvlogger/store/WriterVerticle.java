package hu.dvlogger.store;

import hu.dvlogger.Config;
import hu.dvlogger.ingest.Ingest;
import hu.dvlogger.model.LogEntry;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.json.JsonObject;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

public class WriterVerticle extends AbstractVerticle {
  static final int MAX_REINDEX_BATCHES = 200;

  private final Config cfg; private final MongoStore mongo; private final ArchiveStore archive;
  private final ManticoreIndex index; private final Stats stats;
  private List<LogEntry> buffer = new ArrayList<>();
  /**
   * Batches whose index write failed, retried periodically by {@link #retryReindex()}. Bounded by
   * {@link #maxReindexBatches}: the entries are still safe in Mongo (REINDEX_ON_START can rebuild
   * the index from there), so on overflow the oldest batch is dropped rather than growing without
   * limit.
   */
  private final Deque<List<LogEntry>> reindexQueue = new ArrayDeque<>();
  /** Package-private so tests can shrink this below {@link #MAX_REINDEX_BATCHES} for a fast bounded-queue test. */
  int maxReindexBatches = MAX_REINDEX_BATCHES;
  private boolean flushing = false;
  private MessageConsumer<JsonObject> consumer;

  public WriterVerticle(Config cfg, MongoStore mongo, ArchiveStore archive, ManticoreIndex index, Stats stats) {
    this.cfg = cfg; this.mongo = mongo; this.archive = archive; this.index = index; this.stats = stats;
  }

  @Override public void start(Promise<Void> start) {
    consumer = vertx.eventBus().consumer(Ingest.ADDRESS);
    consumer.setMaxBufferedMessages(cfg.batchSize() * 20);
    consumer.handler(m -> {
      stats.received.incrementAndGet();
      buffer.add(LogEntry.fromMongo(m.body()));
      if (buffer.size() >= cfg.batchSize()) flush();
    });
    vertx.setPeriodic(cfg.batchMs(), t -> flush());
    vertx.setPeriodic(2000, t -> retryReindex());
    start.complete();
  }

  private Future<Void> flush() {
    if (flushing || buffer.isEmpty()) return Future.succeededFuture();
    List<LogEntry> batch = buffer; buffer = new ArrayList<>();
    flushing = true;
    return writeMongo(batch, 3)
      .compose(v -> archive == null ? Future.succeededFuture() : archive.insertMany(batch)
          .recover(t -> { System.err.println("archive write failed: " + t.getMessage()); return Future.succeededFuture(); }))
      .onSuccess(v -> { stats.written.addAndGet(batch.size()); writeIndex(batch); })
      .onFailure(t -> { stats.dropped.addAndGet(batch.size()); System.err.println("mongo write failed, dropped " + batch.size() + ": " + t.getMessage()); })
      .compose(v -> flushDone(), t -> flushDone());
  }

  /** Resets flushing and chains into another flush if the buffer already refilled past batchSize. */
  private Future<Void> flushDone() {
    flushing = false;
    return buffer.size() >= cfg.batchSize() ? flush() : Future.succeededFuture();
  }

  private Future<Void> writeMongo(List<LogEntry> batch, int attempts) {
    return mongo.insertMany(batch).recover(t -> attempts > 1
        ? vertx.timer(250L * (4 - attempts)).compose(v -> writeMongo(batch, attempts - 1))
        : Future.failedFuture(t));
  }

  private void writeIndex(List<LogEntry> batch) {
    index.insertMany(batch).onFailure(t -> {
      stats.indexFailed.incrementAndGet();
      if (reindexQueue.size() >= maxReindexBatches) {
        List<LogEntry> dropped = reindexQueue.pollFirst();
        if (dropped != null) {
          stats.indexDropped.addAndGet(dropped.size());
          System.err.println("reindex queue full (" + maxReindexBatches + " batches), dropping oldest batch of "
              + dropped.size() + " entries (still safe in Mongo; rebuildable via REINDEX_ON_START)");
        }
      }
      reindexQueue.add(batch);
      stats.reindexQueue.set(reindexQueue.size());
    });
  }

  private void retryReindex() {
    List<LogEntry> batch = reindexQueue.poll();
    if (batch == null) return;
    stats.reindexQueue.set(reindexQueue.size());
    index.insertMany(batch).onFailure(t -> { reindexQueue.addFirst(batch); stats.reindexQueue.set(reindexQueue.size()); });
  }

  @Override public void stop(Promise<Void> stop) {
    consumer.unregister().compose(v -> flush()).onComplete(v -> stop.complete());
  }
}
