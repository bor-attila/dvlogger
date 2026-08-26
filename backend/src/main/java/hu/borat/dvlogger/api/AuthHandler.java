package hu.borat.dvlogger.api;

import hu.borat.dvlogger.Config;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import java.security.MessageDigest;

public class AuthHandler {
  static final String SESSION_USER = "user";
  private final Config cfg;
  private final LoginThrottle throttle;
  public AuthHandler(Config cfg) { this(cfg, new LoginThrottle()); }
  public AuthHandler(Config cfg, LoginThrottle throttle) { this.cfg = cfg; this.throttle = throttle; }

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
    String ip = rc.request().remoteAddress() == null ? "?" : rc.request().remoteAddress().hostAddress();
    boolean ok = eq(cfg.authUser(), b.getString("user", "")) && eq(cfg.authPassword(), b.getString("password", ""));
    if (throttle.isBanned(ip)) {
      // Banned: keep counting so the ban slides, and answer exactly like a wrong password — even if it was right.
      throttle.recordFailure(ip);
      badCredentials(rc);
    } else if (ok) {
      throttle.recordSuccess(ip);
      rc.session().regenerateId().put(SESSION_USER, cfg.authUser());
      rc.response().setStatusCode(204).end();
    } else {
      throttle.recordFailure(ip);
      badCredentials(rc);
    }
  }

  /** Arms a periodic sweep so a trickle of distinct IPs cannot grow the throttle map without bound. */
  public void scheduleCleanup(io.vertx.core.Vertx vertx) {
    vertx.setPeriodic(LoginThrottle.WINDOW_MS, t -> throttle.cleanup());
  }

  private static void badCredentials(RoutingContext rc) {
    rc.response().setStatusCode(401).putHeader("content-type", "application/json")
        .end(new JsonObject().put("error", "bad credentials").encode());
  }
  private static boolean eq(String a, String b) { return MessageDigest.isEqual(a.getBytes(), b.getBytes()); }
}
