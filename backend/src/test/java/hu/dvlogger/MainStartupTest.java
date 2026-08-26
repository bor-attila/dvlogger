package hu.dvlogger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Regression test for a real startup hang: Main.main() used to kick off the mongo/manticore/
 * verticle init chain and then return immediately, without blocking. Vert.x creates its own
 * event-loop threads lazily -- only once real work is actually dispatched onto one -- and the
 * initial Mongo/MySQL client calls run their connection handshake entirely on the drivers' own
 * (daemon) threads, never touching a Vert.x context until their result comes back. That let the
 * JVM see zero surviving non-daemon threads the instant main() returned, so it would exit
 * silently (code 0, no exception, no "init failed"/"dvlogger up" line -- only the drivers' own
 * startup log lines) before the init/retry logic or the HTTP listener ever ran. This reproduced
 * 100% of the time locally (see task report) because the shaded/plain classpath's cold class
 * loading was consistently slower than the loopback Mongo/Manticore round trip needed to first
 * touch a Vert.x context.
 *
 * This test runs the real Main class as a separate JVM process (the same way the production
 * entry point is actually invoked) against real Mongo/Manticore containers, and asserts the
 * process is both still alive and answering /api/health well after the old bug would already
 * have killed it silently.
 */
@Testcontainers
class MainStartupTest {
  @Container static GenericContainer<?> manticore = new GenericContainer<>("manticoresearch/manticore:6.3.6")
      .withExposedPorts(9306).waitingFor(Wait.forListeningPort());
  @Container static MongoDBContainer mongo = new MongoDBContainer("mongo:7");

  private Process process;

  @AfterEach void tearDown() {
    if (process != null) process.destroyForcibly();
  }

  @Test @Timeout(60)
  void mainStaysUpAndServesHealth() throws Exception {
    int httpPort = freePort();
    int ingestPort = freePort();
    ProcessBuilder pb = new ProcessBuilder("java", "-cp", System.getProperty("java.class.path"), "hu.dvlogger.Main");
    pb.environment().put("AUTH_USER", "a");
    pb.environment().put("AUTH_PASSWORD", "b");
    pb.environment().put("MONGO_URL", mongo.getConnectionString() + "/dvlogger");
    pb.environment().put("MANTICORE_HOST", manticore.getHost());
    pb.environment().put("MANTICORE_PORT", String.valueOf(manticore.getMappedPort(9306)));
    pb.environment().put("HTTP_PORT", String.valueOf(httpPort));
    pb.environment().put("INGEST_PORT", String.valueOf(ingestPort));
    pb.redirectErrorStream(true);
    pb.redirectOutput(ProcessBuilder.Redirect.DISCARD);
    process = pb.start();

    // The old bug's failure mode was a silent exit within roughly 1-3s of starting, well before
    // the mongo/manticore init chain or the HTTP listener ever ran. Give that ample time to
    // occur before asserting the process is still up.
    Thread.sleep(4000);
    assertTrue(process.isAlive(), "dvlogger process exited prematurely during startup (startup-hang regression)");

    HttpClient http = HttpClient.newHttpClient();
    HttpRequest req = HttpRequest.newBuilder(URI.create("http://localhost:" + httpPort + "/api/health"))
        .timeout(Duration.ofSeconds(2)).GET().build();

    Exception last = null;
    for (int i = 0; i < 30; i++) {
      assertTrue(process.isAlive(), "dvlogger process died while waiting for /api/health");
      try {
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, resp.statusCode());
        return;
      } catch (IOException e) {
        last = e;
        Thread.sleep(500);
      }
    }
    fail("dvlogger never answered /api/health: " + last);
  }

  private static int freePort() throws IOException {
    try (ServerSocket s = new ServerSocket(0)) { return s.getLocalPort(); }
  }
}
