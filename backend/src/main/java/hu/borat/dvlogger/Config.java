package hu.borat.dvlogger;

import java.util.Map;
import java.util.Set;

public record Config(String authUser, String authPassword, boolean archiveEnabled, String logFormat,
                     String logTextPattern, int retentionDays, String mongoUrl, String manticoreHost,
                     int manticorePort, int httpPort, int ingestPort, String ingestToken,
                     int batchSize, int batchMs, boolean reindexOnStart, boolean noFooterText) {

  private static final Set<String> FORMATS = Set.of("text", "json", "gelf", "auto");

  public static Config fromEnv(Map<String, String> env) {
    String user = required(env, "AUTH_USER");
    String pass = required(env, "AUTH_PASSWORD");
    String format = env.getOrDefault("LOG_FORMAT", "auto").toLowerCase();
    if (!FORMATS.contains(format)) throw new IllegalStateException("LOG_FORMAT must be one of " + FORMATS);
    return new Config(user, pass,
        Boolean.parseBoolean(env.getOrDefault("ARCHIVE_ENABLED", "true")),
        format,
        blankToNull(env.get("LOG_TEXT_PATTERN")),
        intOf(env, "RETENTION_DAYS", 14),
        env.getOrDefault("MONGO_URL", "mongodb://mongo:27017/dvlogger"),
        env.getOrDefault("MANTICORE_HOST", "manticore"),
        intOf(env, "MANTICORE_PORT", 9306),
        intOf(env, "HTTP_PORT", 8080),
        intOf(env, "INGEST_PORT", 11222),
        blankToNull(env.get("INGEST_TOKEN")),
        intOf(env, "BATCH_SIZE", 500),
        intOf(env, "BATCH_MS", 200),
        Boolean.parseBoolean(env.getOrDefault("REINDEX_ON_START", "true")),
        Boolean.parseBoolean(env.getOrDefault("NO_FOOTER_TEXT", "false")));
  }

  private static String required(Map<String, String> env, String key) {
    String v = blankToNull(env.get(key));
    if (v == null) throw new IllegalStateException("Missing required env " + key);
    return v;
  }
  private static String blankToNull(String s) { return s == null || s.isBlank() ? null : s; }
  private static int intOf(Map<String, String> env, String key, int def) {
    String v = blankToNull(env.get(key));
    return v == null ? def : Integer.parseInt(v);
  }
}
