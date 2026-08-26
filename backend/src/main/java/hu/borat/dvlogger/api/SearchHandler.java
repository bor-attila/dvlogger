package hu.borat.dvlogger.api;

import hu.borat.dvlogger.model.LogEntry;
import hu.borat.dvlogger.store.ManticoreIndex;
import hu.borat.dvlogger.store.MongoStore;
import hu.borat.dvlogger.store.SearchQuery;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import java.time.DateTimeException;
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
    catch (IllegalArgumentException | DateTimeException e) {
      rc.response().setStatusCode(400).end(new JsonObject().put("error", "bad query: " + e.getMessage()).encode()); return;
    }
    SearchQuery fq = q;
    index.search(fq).compose(ids -> {
      // Cap the candidate id list to the page size *before* deriving the cursor and hydrating, so
      // `next` reflects the last id actually considered for this page - not the last id Mongo happened
      // to still have a document for (a doc can be missing from Mongo, e.g. TTL-expired after indexing,
      // without meaning the index has no more matches beyond it).
      List<String> pageIds = ids.size() > fq.limit() ? ids.subList(0, fq.limit()) : ids;
      String next = pageIds.size() >= fq.limit() ? pageIds.get(pageIds.size() - 1) : null;
      return mongo.findByIds(pageIds).map(items -> page(items, next));
    }).onSuccess(rc::json).onFailure(rc::fail);
  }

  static JsonObject page(List<LogEntry> items, String next) {
    JsonArray arr = new JsonArray(items.stream().map(LogEntry::toApi).toList());
    return new JsonObject().put("items", arr).put("next", next);
  }
}
