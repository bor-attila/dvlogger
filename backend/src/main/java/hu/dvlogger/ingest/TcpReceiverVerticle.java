package hu.dvlogger.ingest;

import hu.dvlogger.Config;
import hu.dvlogger.ingest.parser.LogParser;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.parsetools.RecordParser;

/** Newline- or NUL-delimited frames on cfg.ingestPort(). */
public class TcpReceiverVerticle extends AbstractVerticle {
  private final Config cfg; private final LogParser parser;
  public TcpReceiverVerticle(Config cfg, LogParser parser) { this.cfg = cfg; this.parser = parser; }

  @Override public void start(Promise<Void> start) {
    vertx.createNetServer().connectHandler(sock -> {
      String ip = sock.remoteAddress().hostAddress();
      RecordParser rp = RecordParser.newDelimited("\n", frame -> {
        String line = frame.toString().replace("\0", "").strip();
        if (line.isEmpty()) return;
        vertx.eventBus().send(Ingest.ADDRESS, parser.parse(line, ip).toMongo());
      });
      rp.maxRecordSize(1024 * 1024);
      sock.handler(buf -> {
        // GELF TCP uses NUL delimiters: normalise to newline before framing
        rp.handle(buf.toString().indexOf('\0') >= 0 ? io.vertx.core.buffer.Buffer.buffer(buf.toString().replace('\0', '\n')) : buf);
      });
    }).listen(cfg.ingestPort()).<Void>mapEmpty().onComplete(start);
  }
}
