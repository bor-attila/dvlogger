package hu.borat.dvlogger.ingest;

import hu.borat.dvlogger.Config;
import hu.borat.dvlogger.ingest.parser.LogParser;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.buffer.Buffer;
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
      rp.exceptionHandler(t -> {
        System.err.println("tcp ingest: " + t.getMessage() + " from " + ip + ", closing");
        sock.close();
      });
      sock.handler(buf -> {
        // GELF TCP uses NUL delimiters: normalise to newline before framing, at the byte
        // level (never round-trip through String) so multi-byte UTF-8 sequences split
        // across TCP reads are not corrupted.
        byte[] bytes = buf.getBytes();
        boolean hasNul = false;
        for (byte b : bytes) if (b == 0) { hasNul = true; break; }
        if (hasNul) {
          for (int i = 0; i < bytes.length; i++) if (bytes[i] == 0) bytes[i] = '\n';
          rp.handle(Buffer.buffer(bytes));
        } else {
          rp.handle(buf);
        }
      });
    }).listen(cfg.ingestPort()).<Void>mapEmpty().onComplete(start);
  }
}
