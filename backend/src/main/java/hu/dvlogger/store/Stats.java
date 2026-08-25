package hu.dvlogger.store;

import io.vertx.core.json.JsonObject;
import java.util.concurrent.atomic.AtomicLong;

public class Stats {
  public final AtomicLong received = new AtomicLong(), written = new AtomicLong(),
      dropped = new AtomicLong(), indexFailed = new AtomicLong(), reindexQueue = new AtomicLong();
  public JsonObject toJson() {
    return new JsonObject().put("received", received.get()).put("written", written.get())
        .put("dropped", dropped.get()).put("indexFailed", indexFailed.get()).put("reindexQueue", reindexQueue.get());
  }
}
