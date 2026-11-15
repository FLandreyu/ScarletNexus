package dev.scarletnexus.ai;

import com.fasterxml.jackson.databind.*;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/**
 * Bounded incremental SSE transport; a completed JSON response is never sliced into fake deltas.
 */
public final class ChatStreamTransport {
  private ChatStreamTransport() {}

  public static String body(
      ObjectMapper json, String model, String protocol, List<AIProvider.Message> messages) {
    try {
      var body = new LinkedHashMap<String, Object>();
      body.put("model", model);
      body.put("stream", true);
      body.put("stream_options", Map.of("include_usage", true));
      body.put(protocol.equals("deepseek-json") ? "max_tokens" : "max_completion_tokens", 8192);
      if (protocol.equals("deepseek-json")) body.put("thinking", Map.of("type", "disabled"));
      body.put("messages", messages);
      return json.writeValueAsString(body);
    } catch (Exception e) {
      throw new IllegalArgumentException("Cannot serialize chat request");
    }
  }

  static AIProvider.Usage send(
      HttpClient http,
      URI endpoint,
      String key,
      String body,
      Duration timeout,
      ObjectMapper json,
      Consumer<String> delta) {
    var subscriber = new StreamBody(json, delta);
    var request =
        HttpRequest.newBuilder(endpoint)
            .timeout(timeout)
            .header("Authorization", "Bearer " + key)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build();
    var future =
        http.sendAsync(
            request,
            info -> {
              subscriber.accept = info.statusCode() == 200;
              return subscriber;
            });
    try {
      var response = future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
      if (response.statusCode() != 200)
        throw new AIProvider.Failure(
            response.statusCode() == 401 || response.statusCode() == 403
                ? "AI_AUTHENTICATION_FAILED"
                : "AI_PROVIDER_ERROR",
            false);
      return response.body();
    } catch (TimeoutException e) {
      throw new AIProvider.Failure("AI_TIMEOUT", true);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new AIProvider.Failure("AI_INTERRUPTED", true);
    } catch (ExecutionException e) {
      Throwable cause = e.getCause();
      while (cause.getCause() != null && !(cause instanceof AIProvider.Failure))
        cause = cause.getCause();
      if (cause instanceof AIProvider.Failure f) throw f;
      throw new AIProvider.Failure("AI_TRANSPORT_FAILURE", true);
    } finally {
      subscriber.result.cancel(true);
      subscriber.cancel();
      future.cancel(true);
    }
  }

  static final class StreamBody implements HttpResponse.BodySubscriber<AIProvider.Usage> {
    final CompletableFuture<AIProvider.Usage> result = new CompletableFuture<>();
    final ObjectMapper json;
    final Consumer<String> delta;
    final ByteArrayOutputStream line = new ByteArrayOutputStream();
    final StringBuilder data = new StringBuilder();
    Flow.Subscription subscription;
    boolean accept = true, done, stop;
    int bytes, chars;
    Long input, output;

    StreamBody(ObjectMapper json, Consumer<String> delta) {
      this.json = json;
      this.delta = delta;
    }

    public CompletionStage<AIProvider.Usage> getBody() {
      return result;
    }

    public void onSubscribe(Flow.Subscription s) {
      subscription = s;
      if (!accept) {
        s.cancel();
        result.complete(null);
      } else s.request(1);
    }

    public void onNext(List<ByteBuffer> buffers) {
      if (result.isDone()) return;
      try {
        for (var buffer : buffers)
          while (buffer.hasRemaining() && !result.isDone()) {
            int b = buffer.get() & 255;
            if (++bytes > 2000000) throw new AIProvider.Failure("AI_OUTPUT_TOO_LARGE", false);
            if (b == 10) {
              line();
            } else {
              if (line.size() >= 131072) throw new AIProvider.Failure("AI_OUTPUT_TOO_LARGE", false);
              line.write(b);
            }
          }
        subscription.request(1);
      } catch (Throwable e) {
        cancel();
        result.completeExceptionally(e);
      }
    }

    void line() throws Exception {
      String value = line.toString(StandardCharsets.UTF_8);
      line.reset();
      if (value.endsWith("\r")) value = value.substring(0, value.length() - 1);
      if (value.isEmpty()) {
        event();
      } else if (value.startsWith("data:")) {
        if (!data.isEmpty()) data.append('\n');
        data.append(value.substring(5).stripLeading());
        if (data.length() > 131072) throw new AIProvider.Failure("AI_OUTPUT_TOO_LARGE", false);
      }
    }

    void event() throws Exception {
      if (data.isEmpty()) return;
      String value = data.toString();
      data.setLength(0);
      if (value.equals("[DONE]")) {
        done = true;
        return;
      }
      if (done) throw new AIProvider.Failure("AI_INVALID_STREAM", false);
      var root = json.readTree(value);
      if (root.has("error")) throw new AIProvider.Failure("AI_PROVIDER_ERROR", false);
      var usage = root.path("usage");
      if (!usage.isMissingNode() && !usage.isNull()) {
        input = count(usage.path("prompt_tokens"));
        output = count(usage.path("completion_tokens"));
      }
      for (var choice : root.path("choices")) {
        if (choice.path("index").asInt(0) != 0) continue;
        var content = choice.path("delta").path("content");
        if (choice.path("delta").hasNonNull("refusal"))
          throw new AIProvider.Failure("AI_REFUSED", false);
        if (content.isTextual()) {
          String text = content.asText();
          chars += text.length();
          if (chars > 50000) throw new AIProvider.Failure("AI_OUTPUT_TOO_LARGE", false);
          delta.accept(text);
        }
        var finish = choice.path("finish_reason");
        if (!finish.isMissingNode() && !finish.isNull()) {
          if (!finish.asText().equals("stop"))
            throw new AIProvider.Failure("AI_INCOMPLETE_OUTPUT", false);
          stop = true;
        }
      }
    }

    static Long count(JsonNode value) {
      return value.isIntegralNumber()
              && value.canConvertToLong()
              && value.longValue() >= 0
              && value.longValue() <= 1000000000L
          ? value.longValue()
          : null;
    }

    public void onError(Throwable e) {
      result.completeExceptionally(e);
    }

    public void onComplete() {
      try {
        if (line.size() > 0) line();
        event();
        if (!done || !stop || chars == 0)
          throw new AIProvider.Failure("AI_INCOMPLETE_OUTPUT", false);
        result.complete(
            new AIProvider.Usage(
                input, output, input != null && output != null ? "PROVIDER" : "UNKNOWN"));
      } catch (Throwable e) {
        result.completeExceptionally(e);
      }
    }

    void cancel() {
      if (subscription != null) subscription.cancel();
    }
  }
}
