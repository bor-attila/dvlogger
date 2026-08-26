package hu.borat.dvlogger.api;

import hu.borat.dvlogger.store.ArchiveStore;
import hu.borat.dvlogger.store.SearchQuery;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import java.time.DateTimeException;

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
    catch (IllegalArgumentException | DateTimeException e) {
      rc.response().setStatusCode(400).end(new JsonObject().put("error", "bad query: " + e.getMessage()).encode()); return;
    }
    SearchQuery fq = q;
    archive.search(fq).onSuccess(items -> {
      String next = items.size() >= fq.limit() ? items.get(items.size() - 1).id() : null;
      rc.json(SearchHandler.page(items, next));
    }).onFailure(rc::fail);
  }
}
