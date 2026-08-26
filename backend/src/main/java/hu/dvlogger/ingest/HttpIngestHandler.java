package hu.dvlogger.ingest;

import hu.dvlogger.Config;
import hu.dvlogger.ingest.parser.LogParser;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;

public class HttpIngestHandler {
  private final Vertx vertx; private final Config cfg; private final LogParser parser;
  public HttpIngestHandler(Vertx vertx, Config cfg, LogParser parser) { this.vertx = vertx; this.cfg = cfg; this.parser = parser; }

  public void register(Router router) { router.post("/api/ingest").handler(this::handle); }

  private void handle(RoutingContext rc) {
    if (cfg.ingestToken() != null && !cfg.ingestToken().equals(rc.request().getHeader("X-Ingest-Token"))) {
      rc.response().setStatusCode(401).end(new JsonObject().put("error", "bad ingest token").encode()); return;
    }
    String ip = rc.request().remoteAddress().hostAddress();
    String body = rc.body().asString() == null ? "" : rc.body().asString();
    int n = 0;
    String t = body.stripLeading();
    if (t.startsWith("[")) {
      JsonArray arr;
      try { arr = new JsonArray(t); } catch (Exception e) { arr = null; }
      if (arr != null) {
        for (Object o : arr) { publish(o instanceof JsonObject j ? j.encode() : String.valueOf(o), ip); n++; }
      } else n = publishLines(body, ip);
    } else n = publishLines(body, ip);
    rc.json(new JsonObject().put("accepted", n));
  }

  private int publishLines(String body, String ip) {
    int n = 0;
    for (String line : body.split("\n")) { if (!line.isBlank()) { publish(line.strip(), ip); n++; } }
    return n;
  }
  private void publish(String line, String ip) {
    vertx.eventBus().send(Ingest.ADDRESS, parser.parse(line, ip).toMongo());
  }
}
