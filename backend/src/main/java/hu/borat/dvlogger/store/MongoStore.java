package hu.borat.dvlogger.store;

import com.mongodb.MongoBulkWriteException;
import hu.borat.dvlogger.model.LogEntry;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.mongo.BulkOperation;
import io.vertx.ext.mongo.BulkWriteOptions;
import io.vertx.ext.mongo.FindOptions;
import io.vertx.ext.mongo.IndexOptions;
import io.vertx.ext.mongo.MongoClient;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

public class MongoStore {
  public static final String COLL = "logs";
  protected final MongoClient client;
  private final int retentionDays;

  public MongoStore(MongoClient client, int retentionDays) { this.client = client; this.retentionDays = retentionDays; }

  /**
   * Creates the indexes if missing. The TTL index is never dropped and rebuilt when it is already
   * there: an index build blocks nothing but takes minutes on a large collection, so a changed
   * RETENTION_DAYS is applied in place with collMod, and an unchanged one is left alone.
   */
  public Future<Void> init() {
    long ttl = (long) retentionDays * 86400L;
    return client.listIndexes(COLL)
        .recover(t -> Future.succeededFuture(new JsonArray())) // collection doesn't exist yet
        .compose(indexes -> {
          JsonObject existing = null;
          for (int i = 0; i < indexes.size(); i++) {
            JsonObject idx = indexes.getJsonObject(i);
            if (idx != null && "ts_ttl".equals(idx.getString("name"))) { existing = idx; break; }
          }
          if (existing == null) return createTtlIndex(ttl);
          Number current = existing.getNumber("expireAfterSeconds");
          if (current != null && current.longValue() == ttl) return Future.succeededFuture();
          return client.runCommand("collMod", new JsonObject().put("collMod", COLL)
                  .put("index", new JsonObject().put("name", "ts_ttl").put("expireAfterSeconds", ttl)))
              .<Void>mapEmpty()
              // e.g. a pre-existing non-TTL ts_ttl index, which collMod refuses to convert
              .recover(t -> client.dropIndex(COLL, "ts_ttl").compose(v -> createTtlIndex(ttl)));
        })
        .compose(v -> client.createIndex(COLL, new JsonObject().put("source", 1).put("ts", -1)))
        .compose(v -> client.createIndex(COLL, new JsonObject().put("tags", 1)));
  }

  private Future<Void> createTtlIndex(long ttlSeconds) {
    return client.createIndexWithOptions(COLL, new JsonObject().put("ts", 1),
        new IndexOptions().name("ts_ttl").expireAfter(ttlSeconds, TimeUnit.SECONDS));
  }

  /**
   * Unordered bulk insert so one duplicate key doesn't abort the rest of the batch. Retries of an
   * already-written batch (e.g. WriterVerticle's Mongo retry after a timeout whose write actually
   * succeeded) surface as a MongoBulkWriteException where every error is a duplicate key (11000);
   * that case is treated as success since the documents are already present. Any other error
   * (including a bulk write that is partly duplicates, partly something else) still fails.
   */
  public static Future<Void> bulkInsert(MongoClient client, String coll, List<LogEntry> entries) {
    if (entries.isEmpty()) return Future.succeededFuture();
    List<BulkOperation> ops = entries.stream().map(e -> BulkOperation.createInsert(e.toMongo())).toList();
    Future<Void> write = client.bulkWriteWithOptions(coll, ops, new BulkWriteOptions().setOrdered(false)).mapEmpty();
    return write.recover(t -> {
      if (t instanceof MongoBulkWriteException ex && !ex.getWriteErrors().isEmpty()
          && ex.getWriteErrors().stream().allMatch(e -> e.getCode() == 11000)) {
        return Future.succeededFuture();
      }
      return Future.failedFuture(t);
    });
  }

  public Future<Void> insertMany(List<LogEntry> entries) { return bulkInsert(client, COLL, entries); }

  public Future<List<LogEntry>> findByIds(List<String> ids) {
    if (ids.isEmpty()) return Future.succeededFuture(List.of());
    JsonArray oids = new JsonArray(ids.stream().map(id -> new JsonObject().put("$oid", id)).toList());
    return client.find(COLL, new JsonObject().put("_id", new JsonObject().put("$in", oids))).map(docs -> {
      Map<String, LogEntry> byId = new HashMap<>();
      docs.forEach(d -> { LogEntry e = LogEntry.fromMongo(d); byId.put(e.id(), e); });
      return ids.stream().map(byId::get).filter(Objects::nonNull).toList();
    });
  }

  public Future<List<String>> distinct(String field) {
    return client.distinct(COLL, field, String.class.getName())
        .map(a -> a.stream().map(Object::toString).sorted().toList());
  }

  /**
   * Streams the whole collection in id order, batch by batch (used by REINDEX_ON_START).
   *
   * <p>Iterative on purpose: chaining one {@code compose} per page nested the pages inside each
   * other, and the whole chain unwound synchronously when the last page completed -- a
   * StackOverflowError once the collection was big enough. Here a single promise is completed at
   * the end and each page is started from the previous page's completion callback (re-scheduled on
   * the context when there is one), so the stack depth stays constant.
   */
  public Future<Void> forEachBatch(int size, Function<List<LogEntry>, Future<Void>> fn) {
    Promise<Void> done = Promise.promise();
    nextPage(null, size, fn, done);
    return done.future();
  }

  private void nextPage(String afterId, int size, Function<List<LogEntry>, Future<Void>> fn, Promise<Void> done) {
    JsonObject q = afterId == null ? new JsonObject()
        : new JsonObject().put("_id", new JsonObject().put("$gt", new JsonObject().put("$oid", afterId)));
    client.findWithOptions(COLL, q, new FindOptions().setSort(new JsonObject().put("_id", 1)).setLimit(size))
        .onComplete(found -> {
          if (found.failed()) { done.fail(found.cause()); return; }
          List<JsonObject> docs = found.result();
          if (docs.isEmpty()) { done.complete(); return; }
          List<LogEntry> batch = docs.stream().map(LogEntry::fromMongo).toList();
          String lastId = batch.get(batch.size() - 1).id();
          fn.apply(batch).onComplete(applied -> {
            if (applied.failed()) { done.fail(applied.cause()); return; }
            Context ctx = Vertx.currentContext();
            if (ctx != null) ctx.runOnContext(v -> nextPage(lastId, size, fn, done));
            else nextPage(lastId, size, fn, done);
          });
        });
  }
}
