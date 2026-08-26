package hu.borat.dvlogger.store;

import io.vertx.core.json.JsonObject;
import java.util.concurrent.atomic.AtomicLong;

public class Stats {
  public final AtomicLong received = new AtomicLong(), written = new AtomicLong(),
      dropped = new AtomicLong(), indexFailed = new AtomicLong(), reindexQueue = new AtomicLong(),
      indexDropped = new AtomicLong(),
      /** Messages the event bus discarded because the writer was paused and its queue overflowed. */
      overflowDropped = new AtomicLong();
  public JsonObject toJson() {
    return new JsonObject().put("received", received.get()).put("written", written.get())
        .put("dropped", dropped.get()).put("indexFailed", indexFailed.get()).put("reindexQueue", reindexQueue.get())
        .put("indexDropped", indexDropped.get()).put("overflowDropped", overflowDropped.get());
  }
}
