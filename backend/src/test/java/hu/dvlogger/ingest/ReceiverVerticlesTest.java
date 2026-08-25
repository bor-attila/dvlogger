package hu.dvlogger.ingest;

import hu.dvlogger.Config;
import hu.dvlogger.ingest.parser.Parsers;
import hu.dvlogger.model.LogEntry;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.datagram.DatagramSocket;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(VertxExtension.class)
class ReceiverVerticlesTest {
  Config cfg = Config.fromEnv(Map.of("AUTH_USER","u","AUTH_PASSWORD","p","INGEST_PORT","21222"));

  @Test void tcpLinesArePublished(Vertx vertx, VertxTestContext ctx) {
    var cp = ctx.checkpoint(2);
    vertx.eventBus().<JsonObject>consumer(Ingest.ADDRESS, m -> {
      LogEntry e = LogEntry.fromMongo(m.body());
      ctx.verify(() -> assertTrue(e.message().startsWith("line")));
      cp.flag();
    });
    vertx.deployVerticle(new TcpReceiverVerticle(cfg, Parsers.forConfig(cfg)))
      .compose(id -> vertx.createNetClient().connect(21222, "localhost"))
      .onComplete(ctx.succeeding(sock -> sock.write("app [] line1\napp [] line2\n")));
  }

  @Test void udpDatagramIsPublished(Vertx vertx, VertxTestContext ctx) {
    vertx.eventBus().<JsonObject>consumer(Ingest.ADDRESS, m -> {
      LogEntry e = LogEntry.fromMongo(m.body());
      ctx.verify(() -> { assertEquals("g", e.message()); assertEquals("h", e.source()); });
      ctx.completeNow();
    });
    vertx.deployVerticle(new UdpReceiverVerticle(cfg, Parsers.forConfig(cfg)))
      .onComplete(ctx.succeeding(id -> {
        DatagramSocket s = vertx.createDatagramSocket();
        s.send(Buffer.buffer("{\"version\":\"1.1\",\"host\":\"h\",\"short_message\":\"g\"}"), 21222, "localhost");
      }));
  }
}
