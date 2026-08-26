package hu.borat.dvlogger.store;

import hu.borat.dvlogger.model.LogEntry;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.mysqlclient.MySQLConnectOptions;
import io.vertx.mysqlclient.MySQLPool;
import io.vertx.sqlclient.PoolOptions;
import io.vertx.sqlclient.Row;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;

/** Manticore RT table accessed over its MySQL protocol (port 9306). */
public class ManticoreIndex {
  private final MySQLPool pool;

  public ManticoreIndex(Vertx vertx, String host, int port) {
    this.pool = MySQLPool.pool(vertx,
        new MySQLConnectOptions().setHost(host).setPort(port).setUser("root").setDatabase(""),
        new PoolOptions().setMaxSize(8));
  }

  /** Substring matching needs infix indexing; bare query words are expanded to *word* (see {@link #matchExpr}). */
  static final int MIN_INFIX_LEN = 2;
  private static final String CREATE = "CREATE TABLE IF NOT EXISTS logs (mongo_id string attribute, ts timestamp, "
      + "source string attribute, level uint, tags_text text, message text) min_infix_len='" + MIN_INFIX_LEN + "'";

  /**
   * Creates the table, or recreates it when an older schema without infix indexing is found
   * (the data is rebuilt from Mongo by REINDEX_ON_START, which is on by default).
   */
  public Future<Void> init() {
    return pool.query(CREATE).execute()
        .compose(v -> pool.query("SHOW CREATE TABLE logs").execute())
        .compose(rows -> {
          String ddl = "";
          for (Row r : rows) ddl = String.valueOf(r.getValue(1));
          if (ddl.contains("min_infix_len")) return Future.succeededFuture();
          System.err.println("manticore: table 'logs' lacks min_infix_len -- recreating (REINDEX_ON_START rebuilds it from Mongo)");
          return pool.query("DROP TABLE logs").execute().compose(v -> pool.query(CREATE).execute()).mapEmpty();
        });
  }

  public Future<Void> truncate() { return pool.query("TRUNCATE TABLE logs").execute().mapEmpty(); }

  public Future<Void> insertMany(List<LogEntry> entries) {
    if (entries.isEmpty()) return Future.succeededFuture();
    StringJoiner values = new StringJoiner(",");
    for (LogEntry e : entries) {
      values.add("(" + q(e.id()) + "," + e.ts().getEpochSecond() + "," + q(e.source()) + ","
          + (e.level() == null ? 0 : e.level()) + "," + q(String.join(" ", e.tags())) + "," + q(e.message()) + ")");
    }
    return pool.query("INSERT INTO logs (mongo_id, ts, source, level, tags_text, message) VALUES " + values).execute().mapEmpty();
  }

  public Future<List<String>> search(SearchQuery sq) {
    List<String> where = new ArrayList<>();
    List<String> match = new ArrayList<>();
    if (sq.q() != null && !sq.q().isBlank()) match.add("@message " + matchExpr(sq.q()));
    String tag = sq.tag() == null ? null : sq.tag().replace("\"", "");
    if (tag != null && !tag.isBlank()) match.add("@tags_text \"" + escapeMatch(tag) + "\"");
    if (!match.isEmpty()) where.add("MATCH(" + q(String.join(" ", match)) + ")");
    if (sq.from() != null) where.add("ts >= " + sq.from().getEpochSecond());
    if (sq.to() != null) where.add("ts <= " + sq.to().getEpochSecond());
    if (sq.source() != null) where.add("source = " + q(sq.source()));
    if (sq.level() != null) where.add("level = " + sq.level());
    if (sq.before() != null) where.add("mongo_id < " + q(sq.before()));
    int limit = Math.max(1, Math.min(sq.limit(), 1000));
    String sql = "SELECT mongo_id FROM logs" + (where.isEmpty() ? "" : " WHERE " + String.join(" AND ", where))
        + " ORDER BY mongo_id DESC LIMIT " + limit + " OPTION max_matches=1000";
    return pool.query(sql).execute().map(rows -> {
      List<String> ids = new ArrayList<>();
      for (Row r : rows) ids.add(r.getString("mongo_id"));
      return ids;
    });
  }

  public Future<Void> deleteBefore(Instant cutoff) {
    return pool.query("DELETE FROM logs WHERE ts < " + cutoff.getEpochSecond()).execute().mapEmpty();
  }

  private static String q(String s) {
    return "'" + s.replace("\\", "\\\\").replace("'", "\\'").replace("\0", "") + "'";
  }
  /** Escapes Manticore full-text operators (backslash first) except quotes (so users can phrase-search). */
  /**
   * Turns the user's free text into a Manticore MATCH expression: "quoted phrases" stay exact,
   * every other word becomes *word* so partial words match (Launc -> Launching), and all
   * parts must match (implicit AND). Words shorter than {@link #MIN_INFIX_LEN} get no wildcard.
   */
  static String matchExpr(String q) {
    List<String> parts = new ArrayList<>();
    java.util.regex.Matcher m = java.util.regex.Pattern.compile("\"([^\"]*)\"|(\\S+)").matcher(q);
    while (m.find()) {
      if (m.group(1) != null) { String ph = m.group(1).trim(); if (!ph.isEmpty()) parts.add("\"" + escapeMatch(ph) + "\""); }
      else {
        String w = m.group(2).replace("\"", "");
        if (w.isEmpty()) continue;
        parts.add(w.length() < MIN_INFIX_LEN ? escapeMatch(w) : "*" + escapeMatch(w) + "*");
      }
    }
    return String.join(" ", parts);
  }

  private static String escapeMatch(String s) {
    StringBuilder b = new StringBuilder();
    for (char c : s.toCharArray()) {
      if (c == '\\' || "!@()~/^$<=-|*".indexOf(c) >= 0) b.append('\\');
      b.append(c);
    }
    return b.toString();
  }
}
