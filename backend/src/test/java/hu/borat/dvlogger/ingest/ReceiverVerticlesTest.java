package hu.borat.dvlogger.ingest;

import hu.borat.dvlogger.Config;
import hu.borat.dvlogger.ingest.parser.Parsers;
import hu.borat.dvlogger.model.LogEntry;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.datagram.DatagramSocket;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
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

  @Test void nulDelimitedFrameIsDelivered(Vertx vertx, VertxTestContext ctx) {
    vertx.eventBus().<JsonObject>consumer(Ingest.ADDRESS, m -> {
      LogEntry e = LogEntry.fromMongo(m.body());
      ctx.verify(() -> assertEquals("gelfstyle", e.message()));
      ctx.completeNow();
    });
    vertx.deployVerticle(new TcpReceiverVerticle(cfg, Parsers.forConfig(cfg)))
      .compose(id -> vertx.createNetClient().connect(21222, "localhost"))
      .onComplete(ctx.succeeding(sock -> sock.write(Buffer.buffer(
          "app [] gelfstyle\0".getBytes(StandardCharsets.UTF_8)))));
  }

  @Test void utf8CharSplitAcrossReadsIsPreservedWithNulDelimiter(Vertx vertx, VertxTestContext ctx) {
    // "a€b" - the euro sign is 3 bytes (E2 82 AC) in UTF-8; split the write so the
    // boundary falls inside those 3 bytes, terminated with a NUL (GELF-TCP style) delimiter.
    String expected = "a€b";
    byte[] full = ("app [] " + expected + "\0").getBytes(StandardCharsets.UTF_8);
    int splitAt = ("app [] a".getBytes(StandardCharsets.UTF_8)).length + 1; // mid euro-sign bytes
    byte[] part1 = Arrays.copyOfRange(full, 0, splitAt);
    byte[] part2 = Arrays.copyOfRange(full, splitAt, full.length);

    vertx.eventBus().<JsonObject>consumer(Ingest.ADDRESS, m -> {
      LogEntry e = LogEntry.fromMongo(m.body());
      ctx.verify(() -> assertEquals(expected, e.message()));
      ctx.completeNow();
    });
    vertx.deployVerticle(new TcpReceiverVerticle(cfg, Parsers.forConfig(cfg)))
      .compose(id -> vertx.createNetClient().connect(21222, "localhost"))
      .onComplete(ctx.succeeding(sock -> {
        sock.write(Buffer.buffer(part1));
        vertx.setTimer(50, t -> sock.write(Buffer.buffer(part2)));
      }));
  }

  @Test void oversizeFrameClosesConnectionButServerKeepsAccepting(Vertx vertx, VertxTestContext ctx) {
    var cp = ctx.checkpoint(2);
    vertx.eventBus().<JsonObject>consumer(Ingest.ADDRESS, m -> {
      LogEntry e = LogEntry.fromMongo(m.body());
      ctx.verify(() -> assertEquals("ok", e.message()));
      cp.flag();
    });
    vertx.deployVerticle(new TcpReceiverVerticle(cfg, Parsers.forConfig(cfg)))
      .compose(id -> vertx.createNetClient().connect(21222, "localhost"))
      .onComplete(ctx.succeeding(sock -> {
        sock.closeHandler(v -> {
          cp.flag();
          vertx.createNetClient().connect(21222, "localhost")
            .onComplete(ctx.succeeding(sock2 -> sock2.write("app [] ok\n")));
        });
        byte[] oversized = new byte[1024 * 1024 + 10];
        Arrays.fill(oversized, (byte) 'x');
        sock.write(Buffer.buffer(oversized));
      }));
  }
}
