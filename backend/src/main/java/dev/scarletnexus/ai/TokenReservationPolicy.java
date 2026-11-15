package dev.scarletnexus.ai;

import dev.scarletnexus.common.DomainFailure;
import java.nio.charset.StandardCharsets;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
public class TokenReservationPolicy {
  private final long daily;
  private final int maxBytes;

  public TokenReservationPolicy(Environment env) {
    daily = env.getProperty("app.ai.tokens.daily-limit", Long.class, 0L);
    maxBytes = env.getProperty("app.ai.tokens.max-request-bytes", Integer.class, 100000);
    if (daily < 0 || daily > 1_000_000_000L || maxBytes < 1000 || maxBytes > 1_000_000)
      throw new IllegalArgumentException("Invalid AI token policy");
  }

  public long dailyLimit() {
    return daily;
  }

  public long reserve(String serializedRequest) {
    int bytes = serializedRequest.getBytes(StandardCharsets.UTF_8).length;
    DomainFailure.require(daily > 0, "AI_TOKEN_POLICY_REQUIRED", "请先配置服务端token预算");
    DomainFailure.require(bytes <= maxBytes, "AI_INPUT_TOO_LARGE", "生成请求超过服务器大小限制");
    long estimate = (long) bytes + 4096 + StructuredRequest.OUTPUT_LIMIT;
    DomainFailure.require(estimate <= daily, "AI_BUDGET_EXCEEDED", "单次预留超过当日token配额");
    return estimate;
  }
}
