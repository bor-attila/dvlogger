package hu.dvlogger.store;

import hu.dvlogger.Config;
import hu.dvlogger.ingest.Ingest;
import hu.dvlogger.model.LogEntry;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.eventbus.impl.MessageConsumerImpl;
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
  /** The flush currently in flight (null when idle); awaited by {@link #stop(Promise)}. */
  private Future<Void> inFlight;
  private boolean paused = false;
  private MessageConsumer<JsonObject> consumer;
  /** High-water mark: above this many buffered entries the consumer is paused. */
  private int highWater;
  /** Low-water mark: the consumer is resumed once the buffer drains back to this. */
  private int lowWater;

  public WriterVerticle(Config cfg, MongoStore mongo, ArchiveStore archive, ManticoreIndex index, Stats stats) {
    this.cfg = cfg; this.mongo = mongo; this.archive = archive; this.index = index; this.stats = stats;
  }

  @Override public void start(Promise<Void> start) {
    highWater = cfg.batchSize() * 20;
    lowWater = cfg.batchSize() * 10;
    consumer = vertx.eventBus().consumer(Ingest.ADDRESS);
    // Backpressure has two stages. Stage 1: our own buffer is capped at highWater entries; past
    // that we pause the consumer. Stage 2: while paused, Vert.x queues incoming messages in the
    // consumer's own pending queue, which is bounded by setMaxBufferedMessages -- once *that*
    // overflows, messages are discarded (counted below). Without the pause the setting has no
    // effect at all: the pending queue is only used while the consumer is paused.
    consumer.setMaxBufferedMessages(highWater);
    // MessageConsumer.discardHandler is not on the 4.5.x interface, only on the implementation;
    // guarded by instanceof so a different implementation just means no counter.
    if (consumer instanceof MessageConsumerImpl<JsonObject> impl)
      impl.discardHandler(m -> stats.overflowDropped.incrementAndGet());
    consumer.handler(m -> {
      stats.received.incrementAndGet();
      buffer.add(LogEntry.fromMongo(m.body()));
      if (!paused && buffer.size() >= highWater) { paused = true; consumer.pause(); }
      if (buffer.size() >= cfg.batchSize()) flush();
    });
    vertx.setPeriodic(cfg.batchMs(), t -> flush());
    vertx.setPeriodic(2000, t -> retryReindex());
    start.complete();
  }

  /**
   * Writes at most {@code batchSize} buffered entries. Only one flush runs at a time; whatever is
   * still buffered when it finishes triggers the next flush, so the batch size stays bounded even
   * when the buffer refills faster than Mongo drains it.
   *
   * @return a future completing when this flush (not the whole drain) is done.
   */
  private Future<Void> flush() {
    if (flushing) return inFlight == null ? Future.succeededFuture() : inFlight;
    if (buffer.isEmpty()) return Future.succeededFuture();
    int n = Math.min(buffer.size(), cfg.batchSize());
    List<LogEntry> batch = new ArrayList<>(buffer.subList(0, n));
    buffer.subList(0, n).clear();
    flushing = true;
    Promise<Void> done = Promise.promise();
    inFlight = done.future();
    writeMongo(batch, 3)
      .compose(v -> archive == null ? Future.succeededFuture() : archive.insertMany(batch)
          .recover(t -> { System.err.println("archive write failed: " + t.getMessage()); return Future.succeededFuture(); }))
      .onSuccess(v -> { stats.written.addAndGet(batch.size()); writeIndex(batch); })
      .onFailure(t -> { stats.dropped.addAndGet(batch.size()); System.err.println("mongo write failed, dropped " + batch.size() + ": " + t.getMessage()); })
      .onComplete(ar -> {
        flushing = false;
        inFlight = null;
        if (paused && buffer.size() <= lowWater) { paused = false; consumer.resume(); }
        done.complete();
        // Drain loop: keep flushing batchSize-sized batches while anything is left, instead of
        // letting the buffer grow until one giant batch is written.
        if (!buffer.isEmpty()) vertx.runOnContext(v -> flush());
      });
    return done.future();
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

  /** Flushes repeatedly until nothing is buffered (each flush still capped at batchSize). */
  private Future<Void> drainAll() {
    if (buffer.isEmpty() && !flushing) return Future.succeededFuture();
    return flush().compose(v -> drainAll());
  }

  /** Best-effort: one pass over the queued failed index batches, so a clean stop doesn't lose them. */
  private Future<Void> flushReindexQueue() {
    List<LogEntry> batch = reindexQueue.poll();
    if (batch == null) { stats.reindexQueue.set(0); return Future.succeededFuture(); }
    stats.reindexQueue.set(reindexQueue.size());
    return index.insertMany(batch).otherwiseEmpty().compose(v -> flushReindexQueue());
  }

  @Override public void stop(Promise<Void> stop) {
    // Stop accepting new entries, let the in-flight flush finish, then write out everything that
    // queued up behind it (the old code returned early whenever a flush was in flight, silently
    // losing the buffer).
    consumer.unregister()
      .compose(v -> inFlight == null ? Future.<Void>succeededFuture() : inFlight)
      .compose(v -> drainAll())
      .compose(v -> flushReindexQueue())
      .onComplete(ar -> stop.complete());
  }

  /** Package-private for tests: entries buffered but not yet handed to a flush. */
  int bufferSize() { return buffer.size(); }
  /** Package-private for tests: whether the event-bus consumer is currently paused. */
  boolean isPaused() { return paused; }
  int highWater() { return highWater; }
}
