package dev.scarletnexus.ai;

import com.fasterxml.jackson.databind.*;
import java.net.URI;
import java.net.http.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.List;

/** Chat Completions JSON-schema transport; never writes business data or logs payloads. */
public final class OpenAiCompatibleProvider implements AIProvider {
  private final URI endpoint;
  private final String model;
  private final String key;
  private final Duration timeout;
  private final int retries;
  private final HttpClient http;
  private final ObjectMapper json;

  public OpenAiCompatibleProvider(
      URI base, String model, String key, Duration timeout, int retries, ObjectMapper json) {
    boolean loopback = List.of("127.0.0.1", "localhost", "[::1]").contains(base.getHost());
    if ((!"https".equals(base.getScheme()) && !(loopback && "http".equals(base.getScheme())))
        || base.getHost() == null
        || base.getUserInfo() != null
        || base.getQuery() != null
        || base.getFragment() != null
        || model == null
        || model.isBlank()
        || key == null
        || key.isBlank()
        || key.contains("\n")
        || key.contains("\r")
        || timeout.toMillis() < 100
        || timeout.toSeconds() > 120
        || retries < 0
        || retries > 2) throw new IllegalArgumentException("Invalid AI provider configuration");
    this.endpoint = URI.create(base.toString().replaceAll("/+$", "") + "/chat/completions");
    this.model = model;
    this.key = key;
    this.timeout = timeout;
    this.retries = retries;
    this.json = json.copy().enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    http =
        HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();
  }

  @Override
  public Result generateStructured(String instructions, String input, JsonNode schema) {
    if (instructions == null
        || input == null
        || schema == null
        || !schema.isObject()
        || instructions.length() + input.length() > 100_000)
      throw new Failure("AI_INVALID_REQUEST", false);
    long deadline = System.nanoTime() + timeout.toNanos();
    try {
      String body = StructuredRequest.body(json, model, instructions, input, schema);
      for (int attempt = 0; ; attempt++) {
        long left = deadline - System.nanoTime();
        if (left <= 0) throw new Failure("AI_TIMEOUT", true);
        var request =
            HttpRequest.newBuilder(endpoint)
                .timeout(Duration.ofNanos(left))
                .header("Authorization", "Bearer " + key)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        // Bounded body and request deadline apply while receiving the entire response.
        var response = http.send(request, info -> new LimitedBodySubscriber(2_000_000));
        int status = response.statusCode();
        if (status == 429 || status == 502 || status == 503) {
          if (attempt >= retries) throw new Failure("AI_TEMPORARY_FAILURE", true);
          long wait =
              retryDelay(response.headers().firstValue("Retry-After").orElse(null), attempt);
          if (wait >= (deadline - System.nanoTime()) / 1_000_000)
            throw new Failure("AI_TEMPORARY_FAILURE", true);
          Thread.sleep(wait);
          continue;
        }
        if (status != 200)
          throw new Failure(
              status == 401 || status == 403 ? "AI_AUTH_FAILED" : "AI_UPSTREAM_REJECTED", false);
        JsonNode root = json.readTree(response.body());
        JsonNode choice = root.path("choices").path(0);
        if (!choice.path("message").path("refusal").isMissingNode()
            && !choice.path("message").path("refusal").isNull())
          throw new Failure("AI_REFUSED", false);
        if (!"stop".equals(choice.path("finish_reason").asText()))
          throw new Failure("AI_INCOMPLETE_OUTPUT", false);
        JsonNode content = choice.path("message").path("content");
        if (!content.isTextual()) throw new Failure("AI_INVALID_OUTPUT", false);
        JsonNode document = json.readTree(content.textValue());
        if (document == null || !document.isObject()) throw new Failure("AI_INVALID_OUTPUT", false);
        JsonNode usage = root.path("usage");
        Long in = count(usage.path("prompt_tokens")), out = count(usage.path("completion_tokens"));
        return new Result(
            document, new Usage(in, out, in != null && out != null ? "PROVIDER" : "UNKNOWN"));
      }
    } catch (Failure e) {
      throw e;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new Failure("AI_INTERRUPTED", true);
    } catch (java.net.http.HttpTimeoutException e) {
      throw new Failure("AI_TIMEOUT", true);
    } catch (com.fasterxml.jackson.core.JacksonException e) {
      throw new Failure("AI_INVALID_OUTPUT", false);
    } catch (Exception e) {
      throw new Failure("AI_TRANSPORT_FAILURE", true);
    }
  }

  private static Long count(JsonNode n) {
    return n.isIntegralNumber() && n.canConvertToLong() && n.longValue() >= 0
        ? n.longValue()
        : null;
  }

  static long retryDelay(String value, int attempt) {
    if (value == null) return 250L * (attempt + 1);
    try {
      return Math.max(0, Math.multiplyExact(Long.parseLong(value), 1000));
    } catch (Exception ignored) {
      try {
        return Math.max(
            0,
            Duration.between(
                    Instant.now(),
                    ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant())
                .toMillis());
      } catch (Exception invalid) {
        return 250L * (attempt + 1);
      }
    }
  }
}
