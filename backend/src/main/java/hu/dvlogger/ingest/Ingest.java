package hu.dvlogger.ingest;

import io.vertx.core.buffer.Buffer;
import java.io.ByteArrayInputStream;
import java.util.zip.GZIPInputStream;
import java.util.zip.InflaterInputStream;

public final class Ingest {
  public static final String ADDRESS = "log.in";
  private Ingest() { }

  /** Handles gzip (1f 8b) and zlib (78 xx) payloads; anything else returned as-is. */
  public static Buffer decompress(Buffer b) {
    if (b.length() < 2) return b;
    int b0 = b.getUnsignedByte(0), b1 = b.getUnsignedByte(1);
    try {
      if (b0 == 0x1f && b1 == 0x8b) return Buffer.buffer(new GZIPInputStream(new ByteArrayInputStream(b.getBytes())).readAllBytes());
      if (b0 == 0x78) return Buffer.buffer(new InflaterInputStream(new ByteArrayInputStream(b.getBytes())).readAllBytes());
    } catch (Exception ignored) { }
    return b;
  }
}
