package dev.scarletnexus;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

/** Gateway-only delayed SSE fixture. No AI provider or application data is involved. */
public final class TlsStreamFixture {
  public static void main(String[] args) throws Exception {
    var server = HttpServer.create(new InetSocketAddress("0.0.0.0", 8080), 0);
    server.createContext(
        "/api/test-stream",
        request -> {
          var headers = request.getRequestHeaders();
          String address = headers.getFirst("X-Forwarded-For");
          boolean sanitized =
              headers.getFirst("Forwarded") == null
                  && address != null
                  && !address.contains("198.51.100.9")
                  && address.equals(headers.getFirst("X-Real-IP"))
                  && "https".equals(headers.getFirst("X-Forwarded-Proto"))
                  && headers.getFirst("X-Forwarded-Host") == null
                  && headers.getFirst("X-Forwarded-Port") == null;
          request.getResponseHeaders().set("Content-Type", "text/event-stream");
          request.sendResponseHeaders(200, 0);
          try (var out = request.getResponseBody()) {
            out.write(
                ("event: first\ndata: " + sanitized + "\n\n").getBytes(StandardCharsets.UTF_8));
            out.flush();
            Thread.sleep(2500);
            out.write("event: done\ndata: complete\n\n".getBytes(StandardCharsets.UTF_8));
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
          }
        });
    server.start();
  }
}
