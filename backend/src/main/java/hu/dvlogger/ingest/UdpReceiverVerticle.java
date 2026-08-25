package hu.dvlogger.ingest;

import hu.dvlogger.Config;
import hu.dvlogger.ingest.parser.LogParser;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
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
          String line = Ingest.decompress(full).toString().replace("\0", "").strip();
          if (!line.isEmpty()) vertx.eventBus().send(Ingest.ADDRESS, parser.parse(line, ip).toMongo());
        });
      })
      .listen(cfg.ingestPort(), "0.0.0.0").<Void>mapEmpty().onComplete(start);
  }
}
