package hu.borat.dvlogger.ingest;

import io.vertx.core.buffer.Buffer;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.GZIPInputStream;
import java.util.zip.InflaterInputStream;

public final class Ingest {
  public static final String ADDRESS = "log.in";
  /**
   * Hard cap on the inflated size of a single compressed message. A small crafted payload can
   * inflate to gigabytes ("zip bomb"), so decompression stops at this many bytes and the message
   * is rejected instead of being buffered in full.
   */
  public static final int MAX_DECOMPRESSED = 1024 * 1024;

  private Ingest() { }

  /**
   * Handles gzip (1f 8b) and zlib (78 xx) payloads; anything else returned as-is.
   *
   * @return the inflated buffer, the input itself when it isn't compressed (or can't be inflated),
   *         or {@code null} when the inflated data would exceed {@link #MAX_DECOMPRESSED} --
   *         callers must drop such a message.
   */
  public static Buffer decompress(Buffer b) {
    if (b.length() < 2) return b;
    int b0 = b.getUnsignedByte(0), b1 = b.getUnsignedByte(1);
    if (b0 == 0x1f && b1 == 0x8b) {
      try (InputStream in = new GZIPInputStream(new ByteArrayInputStream(b.getBytes()))) { return readCapped(in); }
      catch (Exception ignored) { return b; }
    }
    if (b0 == 0x78) {
      try (InputStream in = new InflaterInputStream(new ByteArrayInputStream(b.getBytes()))) { return readCapped(in); }
      catch (Exception ignored) { return b; }
    }
    return b;
  }

  /** Reads the stream into a buffer, giving up (returning null) as soon as the cap is exceeded. */
  private static Buffer readCapped(InputStream in) throws IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    byte[] chunk = new byte[8192];
    int total = 0, n;
    while ((n = in.read(chunk)) > 0) {
      total += n;
      if (total > MAX_DECOMPRESSED) return null;
      out.write(chunk, 0, n);
    }
    return Buffer.buffer(out.toByteArray());
  }
}
