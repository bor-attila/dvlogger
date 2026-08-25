package hu.dvlogger.api;

import hu.dvlogger.Config;
import hu.dvlogger.store.ArchiveStore;
import hu.dvlogger.store.ManticoreIndex;
import hu.dvlogger.store.MongoStore;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.StaticHandler;

public class ApiVerticle extends AbstractVerticle {
  private final Config cfg;
  private final MongoStore mongo;
  private final ArchiveStore archive;
  private final ManticoreIndex index;

  public ApiVerticle(Config cfg, MongoStore mongo, ArchiveStore archive, ManticoreIndex index) {
    this.cfg = cfg; this.mongo = mongo; this.archive = archive; this.index = index;
  }

  @Override public void start(Promise<Void> start) {
    Router router = Router.router(vertx);
    router.route().handler(BodyHandler.create().setBodyLimit(10 * 1024 * 1024));
    router.get("/api/health").handler(rc -> rc.json(new JsonObject()
        .put("status", "ok").put("archiveEnabled", cfg.archiveEnabled())));
    registerRoutes(router);
    router.route("/*").handler(StaticHandler.create("webroot").setIndexPage("index.html"));
    // SPA fallback: unknown non-API paths serve index.html
    router.route().last().handler(rc -> {
      if (rc.request().path().startsWith("/api/")) rc.response().setStatusCode(404).end();
      else rc.response().sendFile("webroot/index.html").onFailure(t -> rc.response().setStatusCode(404).end());
    });
    vertx.createHttpServer().requestHandler(router).listen(cfg.httpPort())
        .<Void>mapEmpty().onComplete(start);
  }

  /** Extended by later tasks (auth, search, ingest). */
  protected void registerRoutes(Router router) { }
}
