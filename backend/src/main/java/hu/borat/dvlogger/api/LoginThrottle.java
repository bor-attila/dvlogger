package hu.borat.dvlogger.api;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.function.LongSupplier;

/**
 * Per-IP login throttle: {@value #MAX_FAILURES} failed attempts within {@value #WINDOW_MS} ms
 * ban the IP for {@value #BAN_MS} ms. Failures during a ban push the ban further out.
 * Not thread-safe: call only from the verticle's event-loop thread.
 */
public class LoginThrottle {
  public static final int MAX_FAILURES = 5;
  public static final long WINDOW_MS = 60_000L;
  public static final long BAN_MS = 5 * 60_000L;

  private static final class Entry {
    final Deque<Long> failures = new ArrayDeque<>();
    long bannedUntil;
  }

  private final Map<String, Entry> byIp = new HashMap<>();
  private final LongSupplier clock;

  public LoginThrottle() { this(System::currentTimeMillis); }
  public LoginThrottle(LongSupplier clock) { this.clock = clock; }

  public boolean isBanned(String ip) {
    Entry e = byIp.get(ip);
    return e != null && e.bannedUntil > clock.getAsLong();
  }

  public void recordFailure(String ip) {
    long now = clock.getAsLong();
    Entry e = byIp.computeIfAbsent(ip, k -> new Entry());
    if (e.bannedUntil > now) { e.bannedUntil = now + BAN_MS; return; }
    e.failures.addLast(now);
    while (!e.failures.isEmpty() && e.failures.peekFirst() <= now - WINDOW_MS) e.failures.pollFirst();
    if (e.failures.size() >= MAX_FAILURES) {
      e.bannedUntil = now + BAN_MS;
      e.failures.clear();
    }
  }

  public void recordSuccess(String ip) { byIp.remove(ip); }

  /** Drops entries that are neither banned nor have failures inside the window. */
  public void cleanup() {
    long now = clock.getAsLong();
    byIp.entrySet().removeIf(en -> {
      Entry e = en.getValue();
      while (!e.failures.isEmpty() && e.failures.peekFirst() <= now - WINDOW_MS) e.failures.pollFirst();
      return e.bannedUntil <= now && e.failures.isEmpty();
    });
  }

  int size() { return byIp.size(); }
}
