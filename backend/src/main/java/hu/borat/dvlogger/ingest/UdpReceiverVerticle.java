package hu.borat.dvlogger.ingest;

import hu.borat.dvlogger.Config;
import hu.borat.dvlogger.ingest.parser.LogParser;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.datagram.DatagramSocketOptions;

public class UdpReceiverVerticle extends AbstractVerticle {
  private final Config cfg; private final LogParser parser;
  private final GelfChunkAssembler assembler = new GelfChunkAssembler(5000);
  public UdpReceiverVerticle(Config cfg, LogParser parser) { this.cfg = cfg; this.parser = parser; }

  @Override public void start(Promise<Void> start) {
    vertx.setPeriodic(5000, t -> assembler.evictOlderThan(System.currentTimeMillis()));
    vertx.createDatagramSocket(new DatagramSocketOptions().setReceiveBufferSize(4 * 1024 * 1024))
      .handler(packet -> {
        String ip = packet.sender().hostAddress();
        assembler.offer(packet.data(), System.currentTimeMillis()).ifPresent(full -> {
          Buffer data = Ingest.decompress(full);
          if (data == null) { // zip bomb guard: inflated payload over Ingest.MAX_DECOMPRESSED
            System.err.println("udp ingest: decompressed payload over " + Ingest.MAX_DECOMPRESSED
                + " bytes, dropping message from " + ip);
            return;
          }
          String line = data.toString().replace("\0", "").strip();
          if (!line.isEmpty()) vertx.eventBus().send(Ingest.ADDRESS, parser.parse(line, ip).toMongo());
        });
      })
      .listen(cfg.ingestPort(), "0.0.0.0").<Void>mapEmpty().onComplete(start);
  }
}
