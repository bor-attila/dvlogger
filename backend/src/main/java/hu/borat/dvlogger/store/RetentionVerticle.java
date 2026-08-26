package hu.borat.dvlogger.store;

import hu.borat.dvlogger.Config;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import java.time.Duration;
import java.time.Instant;

/**
 * Deletes documents older than RETENTION_DAYS from the Manticore index, at start and every hour.
 * Mongo's TTL index handles {@code logs} itself; {@code logs_archive} is untouched (kept forever).
 */
public class RetentionVerticle extends AbstractVerticle {
  private final Config cfg;
  private final ManticoreIndex index;

  public RetentionVerticle(Config cfg, ManticoreIndex index) { this.cfg = cfg; this.index = index; }

  @Override public void start(Promise<Void> start) {
    run().onComplete(v -> start.complete());
    vertx.setPeriodic(Duration.ofHours(1).toMillis(), t -> run());
  }

  private Future<Void> run() {
    Instant cutoff = Instant.now().minus(Duration.ofDays(cfg.retentionDays()));
    return index.deleteBefore(cutoff).onFailure(t -> System.err.println("retention delete failed: " + t.getMessage()));
  }
}
