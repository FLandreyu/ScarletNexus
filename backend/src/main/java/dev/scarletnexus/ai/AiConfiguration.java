package dev.scarletnexus.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.time.Duration;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;

@Configuration
public class AiConfiguration {
  @Bean
  AIProvider aiProvider(Environment env, ObjectMapper json) {
    if (!env.getProperty("app.ai.enabled", Boolean.class, false))
      return (instructions, input, schema) -> {
        throw new AIProvider.Failure("AI_NOT_CONFIGURED", false);
      };
    return new OpenAiCompatibleProvider(
        URI.create(env.getRequiredProperty("app.ai.base-url")),
        env.getRequiredProperty("app.ai.model"),
        env.getRequiredProperty("app.ai.key"),
        Duration.ofSeconds(env.getProperty("app.ai.timeout-seconds", Integer.class, 60)),
        env.getProperty("app.ai.retries", Integer.class, 0),
        json,
        env.getProperty("app.ai.protocol", "json-schema"));
  }
}
