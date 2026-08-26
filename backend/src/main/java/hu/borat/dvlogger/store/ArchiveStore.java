package hu.borat.dvlogger.store;

import hu.borat.dvlogger.model.LogEntry;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.mongo.FindOptions;
import io.vertx.ext.mongo.MongoClient;
import java.util.List;
import java.util.regex.Pattern;

public class ArchiveStore {
  public static final String COLL = "logs_archive";
  private final MongoClient client;
  public ArchiveStore(MongoClient client) { this.client = client; }

  public Future<Void> init() {
    return client.createIndex(COLL, new JsonObject().put("ts", -1))
        .compose(v -> client.createIndex(COLL, new JsonObject().put("source", 1).put("ts", -1)))
        .compose(v -> client.createIndex(COLL, new JsonObject().put("tags", 1).put("ts", -1)))
        .compose(v -> client.createIndex(COLL, new JsonObject().put("level", 1)))
        .compose(v -> client.createIndex(COLL, new JsonObject().put("message", "text")));
  }

  public Future<Void> insertMany(List<LogEntry> entries) { return MongoStore.bulkInsert(client, COLL, entries); }

  public Future<List<String>> distinct(String field) {
    return client.distinct(COLL, field, String.class.getName()).map(a -> a.stream().map(Object::toString).sorted().toList());
  }

  /** Builds the Mongo filter for a SearchQuery. Package-private for reuse/testing. */
  static JsonObject filter(SearchQuery q) {
    JsonObject f = new JsonObject();
    if (q.from() != null || q.to() != null) {
      JsonObject ts = new JsonObject();
      if (q.from() != null) ts.put("$gte", new JsonObject().put("$date", q.from().toString()));
      if (q.to() != null) ts.put("$lte", new JsonObject().put("$date", q.to().toString()));
      f.put("ts", ts);
    }
    if (q.source() != null) f.put("source", q.source());
    if (q.tag() != null) f.put("tags", q.tag());
    if (q.level() != null) f.put("level", q.level());
    if (q.q() != null && !q.q().isBlank()) {
      String s = q.q().trim();
      if (s.length() >= 2 && s.startsWith("\"") && s.endsWith("\""))
        f.put("message", new JsonObject().put("$regex", Pattern.quote(s.substring(1, s.length() - 1))).put("$options", "i"));
      else f.put("$text", new JsonObject().put("$search", s));
    }
    if (q.before() != null) f.put("_id", new JsonObject().put("$lt", new JsonObject().put("$oid", q.before())));
    return f;
  }

  public Future<List<LogEntry>> search(SearchQuery q) {
    FindOptions opts = new FindOptions().setSort(new JsonObject().put("_id", -1)).setLimit(Math.max(1, Math.min(q.limit(), 1000)));
    return client.findWithOptions(COLL, filter(q), opts).map(docs -> docs.stream().map(LogEntry::fromMongo).toList());
  }
}
