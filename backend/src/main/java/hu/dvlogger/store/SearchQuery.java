package hu.dvlogger.store;

import java.time.Instant;

/** before = cursor: id of the last item of the previous page (page sorted by ts desc, id desc). */
public record SearchQuery(String q, Instant from, Instant to, String source, String tag, Integer level,
                          int limit, String before) { }
