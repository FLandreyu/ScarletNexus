package dev.scarletnexus.ai;

import com.fasterxml.jackson.databind.JsonNode;

/** Transport boundary only. Callers must reserve budget and validate returned drafts. */
public interface AIProvider {
  Result generateStructured(String instructions, String input, JsonNode schema);

  record Usage(Long inputTokens, Long outputTokens, String source) {}

  record Result(JsonNode document, Usage usage) {}

  final class Failure extends RuntimeException {
    private final String code;
    private final boolean retryable;

    public Failure(String code, boolean retryable) {
      super(code);
      this.code = code;
      this.retryable = retryable;
    }

    public String code() {
      return code;
    }

    public boolean retryable() {
      return retryable;
    }
  }
}
