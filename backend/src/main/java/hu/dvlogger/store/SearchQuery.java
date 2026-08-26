package hu.dvlogger.store;

import java.time.Instant;

/** before = cursor: id of the last item of the previous page (page sorted by ts desc, id desc). */
public record SearchQuery(String q, Instant from, Instant to, String source, String tag, Integer level,
                          int limit, String before) {

  public static SearchQuery fromParams(io.vertx.core.MultiMap p) {
    Instant from = instant(p.get("from")), to = instant(p.get("to"));
    String last = blank(p.get("last"));
    if (last != null) {
      long lastMinutes = Long.parseLong(last);
      if (lastMinutes < 0) throw new IllegalArgumentException("last must be >= 0");
      from = Instant.now().minusSeconds(lastMinutes * 60); to = null;
    }
    String lvl = blank(p.get("level"));
    String lim = blank(p.get("limit"));
    int limit = lim == null ? 100 : Integer.parseInt(lim);
    limit = Math.max(1, Math.min(limit, 1000));
    return new SearchQuery(blank(p.get("q")), from, to, blank(p.get("source")), blank(p.get("tag")),
        lvl == null ? null : Integer.parseInt(lvl), limit, blank(p.get("before")));
  }
  private static String blank(String s) { return s == null || s.isBlank() ? null : s.trim(); }
  private static Instant instant(String s) { s = blank(s); return s == null ? null : Instant.parse(s); }
}
