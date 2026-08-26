package hu.borat.dvlogger.api;

import hu.borat.dvlogger.Config;
import hu.borat.dvlogger.ingest.HttpIngestHandler;
import hu.borat.dvlogger.ingest.parser.LogParser;
import hu.borat.dvlogger.store.ArchiveStore;
import hu.borat.dvlogger.store.ManticoreIndex;
import hu.borat.dvlogger.store.MongoStore;
import hu.borat.dvlogger.store.Stats;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.http.CookieSameSite;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.SessionHandler;
import io.vertx.ext.web.handler.StaticHandler;
import io.vertx.ext.web.sstore.LocalSessionStore;

public class ApiVerticle extends AbstractVerticle {
  private final Config cfg;
  private final MongoStore mongo;
  private final ArchiveStore archive;
  private final ManticoreIndex index;
  protected final Stats stats;
  protected final LogParser parser;

  public ApiVerticle(Config cfg, MongoStore mongo, ArchiveStore archive, ManticoreIndex index, Stats stats, LogParser parser) {
    this.cfg = cfg; this.mongo = mongo; this.archive = archive; this.index = index;
    this.stats = stats; this.parser = parser;
  }

  @Override public void start(Promise<Void> start) {
    Router router = Router.router(vertx);
    router.route().handler(BodyHandler.create().setBodyLimit(10 * 1024 * 1024));
    router.get("/api/health").handler(rc -> rc.json(new JsonObject()
        .put("status", "ok").put("archiveEnabled", cfg.archiveEnabled())
        .put("stats", stats == null ? new JsonObject() : stats.toJson())));
    SessionHandler sessionHandler = SessionHandler.create(LocalSessionStore.create(vertx))
        .setCookieHttpOnlyFlag(true).setCookieSameSite(CookieSameSite.STRICT);
    router.route().handler(rc -> {
      if (rc.request().path().equals("/api/ingest")) rc.next();
      else sessionHandler.handle(rc);
    });
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

  protected void registerRoutes(Router router) {
    AuthHandler auth = new AuthHandler(cfg);
    auth.register(router);
    new HttpIngestHandler(vertx, cfg, parser).register(router);
    router.route("/api/*").handler(auth.required());   // everything registered after this line needs login
    registerProtectedRoutes(router, auth);
  }
  /** Search routes (Task 10). */
  protected void registerProtectedRoutes(Router router, AuthHandler auth) {
    new SearchHandler(mongo, index).register(router);
    new ArchiveSearchHandler(archive).register(router);
    router.route("/api/*").failureHandler(rc -> {
      Throwable t = rc.failure();
      if (t != null) t.printStackTrace();
      rc.response().setStatusCode(rc.statusCode() > 0 ? rc.statusCode() : 500)
          .end(new JsonObject().put("error", t == null ? "error" : t.getMessage()).encode());
    });
  }
}
