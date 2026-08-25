package hu.dvlogger.ingest;

import io.vertx.core.buffer.Buffer;
import org.junit.jupiter.api.Test;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;

class GelfChunkAssemblerTest {
  static Buffer chunk(long msgId, int seq, int total, String payload) {
    return Buffer.buffer().appendByte((byte) 0x1e).appendByte((byte) 0x0f).appendLong(msgId)
        .appendByte((byte) seq).appendByte((byte) total).appendString(payload);
  }
  @Test void reassemblesOutOfOrder() {
    GelfChunkAssembler a = new GelfChunkAssembler(5000);
    assertTrue(a.offer(chunk(7, 1, 2, "world"), 0).isEmpty());
    Optional<Buffer> full = a.offer(chunk(7, 0, 2, "hello "), 0);
    assertEquals("hello world", full.orElseThrow().toString());
  }
  @Test void nonChunkedPassesThrough() {
    GelfChunkAssembler a = new GelfChunkAssembler(5000);
    assertEquals("plain", a.offer(Buffer.buffer("plain"), 0).orElseThrow().toString());
  }
  @Test void expiredChunksDropped() {
    GelfChunkAssembler a = new GelfChunkAssembler(1000);
    a.offer(chunk(9, 0, 2, "a"), 0);
    a.evictOlderThan(2000);
    assertTrue(a.offer(chunk(9, 1, 2, "b"), 2000).isEmpty());
  }
  @Test void gzipIsDecompressed() throws Exception {
    java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
    try (var gz = new java.util.zip.GZIPOutputStream(bos)) { gz.write("zipped".getBytes()); }
    assertEquals("zipped", Ingest.decompress(Buffer.buffer(bos.toByteArray())).toString());
  }
}
