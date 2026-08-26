package hu.dvlogger.api;

import hu.dvlogger.Config;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import java.security.MessageDigest;

public class AuthHandler {
  static final String SESSION_USER = "user";
  private final Config cfg;
  public AuthHandler(Config cfg) { this.cfg = cfg; }

  public void register(Router router) {
    router.post("/api/login").handler(this::login);
    router.post("/api/logout").handler(rc -> { rc.session().destroy(); rc.response().setStatusCode(204).end(); });
    router.get("/api/me").handler(rc -> {
      String user = rc.session() == null ? null : rc.session().get(SESSION_USER);
      if (user == null) rc.response().setStatusCode(401).putHeader("content-type", "application/json")
          .end(new JsonObject().put("error", "unauthorized").encode());
      else rc.json(new JsonObject().put("user", user));
    });
  }

  /** Put on every /api/* route that needs a login. */
  public Handler<RoutingContext> required() {
    return rc -> {
      if (rc.session() != null && rc.session().get(SESSION_USER) != null) rc.next();
      else rc.response().setStatusCode(401).putHeader("content-type", "application/json")
          .end(new JsonObject().put("error", "unauthorized").encode());
    };
  }

  private void login(RoutingContext rc) {
    JsonObject b;
    try { b = rc.body().asJsonObject(); } catch (Exception e) { b = null; }
    if (b == null) b = new JsonObject();
    if (eq(cfg.authUser(), b.getString("user", "")) && eq(cfg.authPassword(), b.getString("password", ""))) {
      rc.session().regenerateId().put(SESSION_USER, cfg.authUser());
      rc.response().setStatusCode(204).end();
    } else {
      rc.response().setStatusCode(401).putHeader("content-type", "application/json")
          .end(new JsonObject().put("error", "bad credentials").encode());
    }
  }
  private static boolean eq(String a, String b) { return MessageDigest.isEqual(a.getBytes(), b.getBytes()); }
}
