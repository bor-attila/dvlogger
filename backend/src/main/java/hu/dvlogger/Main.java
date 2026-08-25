package hu.dvlogger;

import hu.dvlogger.api.ApiVerticle;
import io.vertx.core.Vertx;

public class Main {
  public static void main(String[] args) {
    Config cfg = Config.fromEnv(System.getenv());
    Vertx vertx = Vertx.vertx();
    vertx.deployVerticle(new ApiVerticle(cfg, null, null, null))
        .onSuccess(id -> System.out.println("dvlogger HTTP on " + cfg.httpPort()))
        .onFailure(t -> { t.printStackTrace(); System.exit(1); });
  }
}
