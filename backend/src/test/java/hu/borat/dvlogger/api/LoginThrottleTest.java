package hu.borat.dvlogger.api;

import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.jupiter.api.Assertions.*;

class LoginThrottleTest {
  final AtomicLong now = new AtomicLong(1_000_000L);
  final LoginThrottle t = new LoginThrottle(now::get);

  @Test void notBannedBeforeFiveFailures() {
    for (int i = 0; i < 4; i++) t.recordFailure("1.2.3.4");
    assertFalse(t.isBanned("1.2.3.4"));
  }

  @Test void fiveFailuresWithinAMinuteBanForFiveMinutes() {
    for (int i = 0; i < 5; i++) t.recordFailure("1.2.3.4");
    assertTrue(t.isBanned("1.2.3.4"));
    now.addAndGet(LoginThrottle.BAN_MS - 1);
    assertTrue(t.isBanned("1.2.3.4"));
    now.addAndGet(2);
    assertFalse(t.isBanned("1.2.3.4"));
  }

  @Test void failuresOutsideWindowDoNotCount() {
    for (int i = 0; i < 4; i++) t.recordFailure("1.2.3.4");
    now.addAndGet(LoginThrottle.WINDOW_MS + 1);
    t.recordFailure("1.2.3.4");
    assertFalse(t.isBanned("1.2.3.4"));
  }

  @Test void banIsPerIp() {
    for (int i = 0; i < 5; i++) t.recordFailure("1.2.3.4");
    assertFalse(t.isBanned("5.6.7.8"));
  }

  @Test void successClearsFailures() {
    for (int i = 0; i < 4; i++) t.recordFailure("1.2.3.4");
    t.recordSuccess("1.2.3.4");
    t.recordFailure("1.2.3.4");
    assertFalse(t.isBanned("1.2.3.4"));
  }

  @Test void failuresWhileBannedExtendTheBan() {
    for (int i = 0; i < 5; i++) t.recordFailure("1.2.3.4");
    now.addAndGet(LoginThrottle.BAN_MS - 1000);
    t.recordFailure("1.2.3.4");
    now.addAndGet(2000);
    assertTrue(t.isBanned("1.2.3.4"));
  }

  @Test void cleanupDropsExpiredEntries() {
    for (int i = 0; i < 5; i++) t.recordFailure("1.2.3.4");
    t.recordFailure("9.9.9.9");
    now.addAndGet(LoginThrottle.BAN_MS + 1);
    t.cleanup();
    assertEquals(0, t.size());
  }
}
