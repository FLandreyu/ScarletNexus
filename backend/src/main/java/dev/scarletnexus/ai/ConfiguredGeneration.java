package dev.scarletnexus.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.scarletnexus.common.DomainFailure;
import java.util.UUID;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class ConfiguredGeneration {
  private final Environment env;
  private final TokenReservationPolicy tokens;
  private final GraphGenerationContract contract;
  private final MoneyLedger money;
  private final GenerationJobs jobs;
  private final AIProvider provider;
  private final ObjectMapper json;

  public ConfiguredGeneration(
      Environment env,
      TokenReservationPolicy tokens,
      GraphGenerationContract contract,
      MoneyLedger money,
      GenerationJobs jobs,
      AIProvider provider,
      ObjectMapper json) {
    this.env = env;
    this.tokens = tokens;
    this.contract = contract;
    this.money = money;
    this.jobs = jobs;
    this.provider = provider;
    this.json = json;
  }

  public boolean enabled() {
    return env.getProperty("app.ai.generation-enabled", Boolean.class, false)
        && env.getProperty("app.ai.enabled", Boolean.class, false)
        && tokens.dailyLimit() > 0;
  }

  public GenerationJobs.View generate(UUID skill, String key, String input) {
    if (!enabled())
      throw new DomainFailure(
          HttpStatus.SERVICE_UNAVAILABLE, "AI_NOT_CONFIGURED", "AI生成尚未启用，手动草案可继续使用");
    String request;
    try {
      request =
          StructuredRequest.body(
              json,
              env.getRequiredProperty("app.ai.model"),
              GraphGenerationContract.INSTRUCTIONS,
              input,
              contract.schema());
    } catch (Exception e) {
      throw new IllegalStateException("Cannot prepare generation request");
    }
    long reservation = tokens.reserve(request);
    money.validateEstimate(
        reservation - StructuredRequest.OUTPUT_LIMIT, StructuredRequest.OUTPUT_LIMIT);
    return jobs.view(
        jobs.generate(skill, key, input, reservation, tokens.dailyLimit(), provider).id());
  }
}
