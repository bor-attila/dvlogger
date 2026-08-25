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
  private final Config cfg; private final MongoStore mongo; private final ArchiveStore archive;
  private final ManticoreIndex index; private final Stats stats;
  private List<LogEntry> buffer = new ArrayList<>();
  private final Deque<List<LogEntry>> reindexQueue = new ArrayDeque<>();
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

  private void flush() {
    if (flushing || buffer.isEmpty()) return;
    List<LogEntry> batch = buffer; buffer = new ArrayList<>();
    flushing = true;
    writeMongo(batch, 3)
      .compose(v -> archive == null ? Future.succeededFuture() : archive.insertMany(batch)
          .recover(t -> { System.err.println("archive write failed: " + t.getMessage()); return Future.succeededFuture(); }))
      .onSuccess(v -> { stats.written.addAndGet(batch.size()); writeIndex(batch); })
      .onFailure(t -> { stats.dropped.addAndGet(batch.size()); System.err.println("mongo write failed, dropped " + batch.size() + ": " + t.getMessage()); })
      .onComplete(v -> { flushing = false; if (buffer.size() >= cfg.batchSize()) flush(); });
  }

  private Future<Void> writeMongo(List<LogEntry> batch, int attempts) {
    return mongo.insertMany(batch).recover(t -> attempts > 1 ? writeMongo(batch, attempts - 1) : Future.failedFuture(t));
  }

  private void writeIndex(List<LogEntry> batch) {
    index.insertMany(batch).onFailure(t -> {
      stats.indexFailed.incrementAndGet();
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
    consumer.unregister().onComplete(v -> { flush(); stop.complete(); });
  }
}
