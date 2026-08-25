package hu.dvlogger.store;

import com.mongodb.MongoBulkWriteException;
import hu.dvlogger.model.LogEntry;
import io.vertx.core.Future;
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

  public Future<Void> init() {
    return client.dropIndex(COLL, "ts_ttl").recover(t -> Future.succeededFuture())
        .compose(v -> client.createIndexWithOptions(COLL, new JsonObject().put("ts", 1),
            new IndexOptions().name("ts_ttl").expireAfter((long) retentionDays * 86400L, TimeUnit.SECONDS)))
        .compose(v -> client.createIndex(COLL, new JsonObject().put("source", 1).put("ts", -1)))
        .compose(v -> client.createIndex(COLL, new JsonObject().put("tags", 1)));
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

  /** Streams the whole collection in id order, batch by batch (used by REINDEX_ON_START). */
  public Future<Void> forEachBatch(int size, Function<List<LogEntry>, Future<Void>> fn) {
    return page(null, size, fn);
  }
  private Future<Void> page(String afterId, int size, Function<List<LogEntry>, Future<Void>> fn) {
    JsonObject q = afterId == null ? new JsonObject()
        : new JsonObject().put("_id", new JsonObject().put("$gt", new JsonObject().put("$oid", afterId)));
    return client.findWithOptions(COLL, q, new FindOptions().setSort(new JsonObject().put("_id", 1)).setLimit(size))
        .compose(docs -> {
          if (docs.isEmpty()) return Future.succeededFuture();
          List<LogEntry> batch = docs.stream().map(LogEntry::fromMongo).toList();
          return fn.apply(batch).compose(v -> page(batch.get(batch.size() - 1).id(), size, fn));
        });
  }
}
