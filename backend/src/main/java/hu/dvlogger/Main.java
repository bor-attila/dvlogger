package hu.dvlogger;

import hu.dvlogger.api.ApiVerticle;
import hu.dvlogger.ingest.TcpReceiverVerticle;
import hu.dvlogger.ingest.UdpReceiverVerticle;
import hu.dvlogger.ingest.parser.LogParser;
import hu.dvlogger.ingest.parser.Parsers;
import hu.dvlogger.store.*;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.mongo.MongoClient;
import java.util.concurrent.CountDownLatch;

public class Main {
  public static void main(String[] args) throws InterruptedException {
    Config cfg = Config.fromEnv(System.getenv());
    Vertx vertx = Vertx.vertx();
    MongoClient client = MongoClient.createShared(vertx, new JsonObject().put("connection_string", cfg.mongoUrl()));
    MongoStore mongo = new MongoStore(client, cfg.retentionDays());
    ArchiveStore archive = cfg.archiveEnabled() ? new ArchiveStore(client) : null;
    ManticoreIndex index = new ManticoreIndex(vertx, cfg.manticoreHost(), cfg.manticorePort());
    Stats stats = new Stats();
    LogParser parser = Parsers.forConfig(cfg);

    Future<String> startup = retry(() -> mongo.init().compose(v -> archive == null ? Future.succeededFuture() : archive.init()).compose(v -> index.init()), 30, vertx)
      .compose(v -> cfg.reindexOnStart() ? reindex(mongo, index) : Future.succeededFuture())
      .compose(v -> vertx.deployVerticle(new WriterVerticle(cfg, mongo, archive, index, stats)))
      .compose(v -> vertx.deployVerticle(new RetentionVerticle(cfg, index)))
      .compose(v -> vertx.deployVerticle(new TcpReceiverVerticle(cfg, parser)))
      .compose(v -> vertx.deployVerticle(new UdpReceiverVerticle(cfg, parser)))
      .compose(v -> vertx.deployVerticle(new ApiVerticle(cfg, mongo, archive, index, stats, parser)))
      .onSuccess(id -> System.out.println("dvlogger up: http " + cfg.httpPort() + ", ingest tcp/udp " + cfg.ingestPort()))
      .onFailure(t -> { t.printStackTrace(); System.exit(1); });

    // Vert.x creates its event-loop threads lazily -- only once real work is actually
    // dispatched onto one -- and the Mongo/MySQL clients above run their initial handshake on
    // their own (daemon) driver threads, not a Vert.x context. If main() returned here, the
    // main thread (the only non-daemon thread that has run so far) could exit before any
    // Vert.x event-loop thread ever starts, letting the JVM shut down mid-init with no error
    // and no output beyond driver warnings. Block main() until the startup chain settles so a
    // live non-daemon thread spans the whole sequence; once verticles are deployed their bound
    // listeners keep the process running on their own.
    CountDownLatch startupSettled = new CountDownLatch(1);
    startup.onComplete(ar -> startupSettled.countDown());
    startupSettled.await();
  }

  /** Waits for mongo/manticore containers to come up: retries every 2 s. */
  static Future<Void> retry(java.util.function.Supplier<Future<Void>> op, int attempts, Vertx vertx) {
    return op.get().recover(t -> {
      if (attempts <= 1) return Future.failedFuture(t);
      System.err.println("init failed (" + t.getMessage() + "), retrying...");
      return vertx.timer(2000).compose(x -> retry(op, attempts - 1, vertx));
    });
  }

  public static Future<Void> reindex(MongoStore mongo, ManticoreIndex index) {
    System.out.println("REINDEX_ON_START: rebuilding manticore from mongo");
    return index.truncate().compose(v -> mongo.forEachBatch(1000, index::insertMany));
  }
}
