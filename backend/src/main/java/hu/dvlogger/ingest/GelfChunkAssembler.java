package hu.dvlogger.ingest;

import io.vertx.core.buffer.Buffer;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/** Reassembles GELF chunked datagrams (magic 0x1e 0x0f). Not thread-safe: use from one verticle. */
public class GelfChunkAssembler {
  private record Pending(Buffer[] parts, long firstSeen) { }
  private final Map<Long, Pending> pending = new HashMap<>();
  private final long ttlMs;

  public GelfChunkAssembler(long ttlMs) { this.ttlMs = ttlMs; }

  public Optional<Buffer> offer(Buffer datagram, long nowMs) {
    if (datagram.length() < 12 || datagram.getUnsignedByte(0) != 0x1e || datagram.getUnsignedByte(1) != 0x0f)
      return Optional.of(datagram);
    long id = datagram.getLong(2);
    int seq = datagram.getUnsignedByte(10), total = datagram.getUnsignedByte(11);
    if (total == 0 || total > 128 || seq >= total) return Optional.empty();
    Pending p = pending.computeIfAbsent(id, k -> new Pending(new Buffer[total], nowMs));
    if (p.parts.length != total) return Optional.empty();
    p.parts[seq] = datagram.getBuffer(12, datagram.length());
    for (Buffer part : p.parts) if (part == null) return Optional.empty();
    pending.remove(id);
    Buffer out = Buffer.buffer();
    for (Buffer part : p.parts) out.appendBuffer(part);
    return Optional.of(out);
  }

  public void evictOlderThan(long nowMs) {
    pending.entrySet().removeIf(e -> nowMs - e.getValue().firstSeen >= ttlMs);
  }
}
