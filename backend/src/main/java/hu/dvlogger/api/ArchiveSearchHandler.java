package hu.dvlogger.api;

import hu.dvlogger.store.ArchiveStore;
import hu.dvlogger.store.SearchQuery;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;

public class ArchiveSearchHandler {
  private final ArchiveStore archive; // null when disabled
  public ArchiveSearchHandler(ArchiveStore archive) { this.archive = archive; }

  public void register(Router router) {
    router.get("/api/archive/*").handler(rc -> {
      if (archive == null) rc.response().setStatusCode(404).end(new JsonObject().put("error", "archive disabled").encode());
      else rc.next();
    });
    router.get("/api/archive/logs").handler(this::logs);
    router.get("/api/archive/sources").handler(rc -> archive.distinct("source").onSuccess(l -> rc.json(new JsonArray(l))).onFailure(rc::fail));
    router.get("/api/archive/tags").handler(rc -> archive.distinct("tags").onSuccess(l -> rc.json(new JsonArray(l))).onFailure(rc::fail));
  }

  private void logs(RoutingContext rc) {
    SearchQuery q;
    try { q = SearchQuery.fromParams(rc.queryParams()); }
    catch (Exception e) { rc.response().setStatusCode(400).end(new JsonObject().put("error", "bad query: " + e.getMessage()).encode()); return; }
    archive.search(q).onSuccess(items -> rc.json(SearchHandler.page(items, q.limit()))).onFailure(rc::fail);
  }
}
