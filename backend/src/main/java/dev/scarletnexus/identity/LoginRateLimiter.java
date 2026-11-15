package dev.scarletnexus.identity;

import java.util.HashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class LoginRateLimiter {
  private record Bucket(long expiresAt, int attempts) {}

  private final Map<String, Bucket> buckets = new HashMap<>();
  private final int limit;

  public LoginRateLimiter(@Value("${app.login.attempts-per-window:10}") int limit) {
    this.limit = limit;
  }

  public synchronized long retryAfterSeconds(String address) {
    long now = System.currentTimeMillis();
    buckets.values().removeIf(bucket -> bucket.expiresAt() <= now);
    Bucket current = buckets.get(address);
    if (current == null) {
      if (buckets.size() >= 1000) return 300;
      buckets.put(address, new Bucket(now + 300_000, 1));
      return 0;
    }
    if (current.attempts() >= limit) return Math.max(1, (current.expiresAt() - now + 999) / 1000);
    buckets.put(address, new Bucket(current.expiresAt(), current.attempts() + 1));
    return 0;
  }
}
