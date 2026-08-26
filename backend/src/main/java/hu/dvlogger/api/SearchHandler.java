package hu.dvlogger.api;

import hu.dvlogger.model.LogEntry;
import hu.dvlogger.store.ManticoreIndex;
import hu.dvlogger.store.MongoStore;
import hu.dvlogger.store.SearchQuery;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import java.util.List;

public class SearchHandler {
  private final MongoStore mongo; private final ManticoreIndex index;
  public SearchHandler(MongoStore mongo, ManticoreIndex index) { this.mongo = mongo; this.index = index; }

  public void register(Router router) {
    router.get("/api/logs").handler(this::logs);
    router.get("/api/sources").handler(rc -> mongo.distinct("source").onSuccess(l -> rc.json(new JsonArray(l))).onFailure(rc::fail));
    router.get("/api/tags").handler(rc -> mongo.distinct("tags").onSuccess(l -> rc.json(new JsonArray(l))).onFailure(rc::fail));
  }

  private void logs(RoutingContext rc) {
    SearchQuery q;
    try { q = SearchQuery.fromParams(rc.queryParams()); }
    catch (Exception e) { rc.response().setStatusCode(400).end(new JsonObject().put("error", "bad query: " + e.getMessage()).encode()); return; }
    index.search(q).compose(mongo::findByIds).onSuccess(items -> rc.json(page(items, q.limit()))).onFailure(rc::fail);
  }

  /** Caps items to limit defensively (stores are expected to already honor it) and derives the cursor. */
  static JsonObject page(List<LogEntry> items, int limit) {
    List<LogEntry> capped = items.size() > limit ? items.subList(0, limit) : items;
    JsonArray arr = new JsonArray(capped.stream().map(LogEntry::toApi).toList());
    String next = capped.size() >= limit ? capped.get(capped.size() - 1).id() : null;
    return new JsonObject().put("items", arr).put("next", next);
  }
}
