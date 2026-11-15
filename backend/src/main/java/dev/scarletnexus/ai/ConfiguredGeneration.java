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
    return generate(skill, key, input, null);
  }

  public GenerationJobs.View repair(UUID parent, String key, boolean confirmed) {
    DomainFailure.require(confirmed, "CONFIRMATION_REQUIRED", "修复会再次请求模型并占用预算，请明确确认");
    var job = jobs.view(parent);
    var input = jobs.input(parent);
    DomainFailure.require(input.depth() < 2, "AI_REPAIR_LIMIT", "已达到两次修复上限");
    String topic =
        input.inputText()
            + "\n[Repair request: previous output failed validation with "
            + job.errorCode()
            + ". Return a complete graph matching the JSON schema and graph constraints.]";
    DomainFailure.require(topic.length() <= 50000, "AI_REPAIR_INPUT_TOO_LARGE", "原主题过长，请手动调整后重新生成");
    return generate(job.skillId(), key, topic, parent);
  }

  private GenerationJobs.View generate(UUID skill, String key, String input, UUID parent) {
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
        jobs.generate(skill, key, input, reservation, tokens.dailyLimit(), provider, parent).id());
  }
}
