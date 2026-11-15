package dev.scarletnexus.ai;

import dev.scarletnexus.common.DomainFailure;
import java.math.*;
import java.time.LocalDate;
import java.util.UUID;
import org.apache.ibatis.annotations.*;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
@Transactional(propagation = Propagation.MANDATORY)
public class MoneyLedger {
  public record Price(
      String currency,
      String provider,
      String model,
      String version,
      LocalDate date,
      BigDecimal input,
      BigDecimal output,
      BigDecimal reserve,
      BigDecimal dailyLimit) {
    public Price {
      if (currency == null
          || !currency.matches("[A-Z]{3}")
          || provider == null
          || provider.isBlank()
          || provider.length() > 120
          || model == null
          || model.isBlank()
          || model.length() > 120
          || version == null
          || version.isBlank()
          || version.length() > 120
          || date == null
          || input == null
          || output == null
          || reserve == null
          || dailyLimit == null
          || input.signum() < 0
          || output.signum() < 0
          || input.scale() > 12
          || output.scale() > 12
          || input.compareTo(new BigDecimal("1000000")) > 0
          || output.compareTo(new BigDecimal("1000000")) > 0
          || reserve.signum() <= 0
          || reserve.scale() > 8
          || dailyLimit.scale() > 8
          || reserve.compareTo(dailyLimit) > 0
          || dailyLimit.compareTo(new BigDecimal("1000000")) > 0)
        throw new IllegalArgumentException("Invalid AI price policy");
    }

    public BigDecimal calculate(long in, long out) {
      if (in < 0 || out < 0) throw new IllegalArgumentException("Invalid usage");
      return input
          .multiply(BigDecimal.valueOf(in))
          .add(output.multiply(BigDecimal.valueOf(out)))
          .divide(BigDecimal.valueOf(1_000_000), 8, RoundingMode.CEILING);
    }
  }

  public record Cost(
      UUID jobId,
      String currency,
      String providerLabel,
      String model,
      String priceVersion,
      LocalDate priceDate,
      BigDecimal inputPerMillion,
      BigDecimal outputPerMillion,
      BigDecimal reserved,
      BigDecimal charged,
      String source,
      LocalDate budgetDay) {}

  @Mapper
  public interface Store {
    @Insert(
        "INSERT INTO ai_money_days(owner_id,budget_day,currency) VALUES(#{owner},#{day},#{currency}) ON CONFLICT DO NOTHING")
    void day(UUID owner, LocalDate day, String currency);

    @Update(
        "UPDATE ai_money_days SET reserved=reserved+#{p.reserve} WHERE owner_id=#{owner} AND budget_day=#{day} AND currency=#{p.currency} AND reserved+consumed<=#{p.dailyLimit}-#{p.reserve}")
    int reserve(UUID owner, LocalDate day, Price p);

    @Insert(
        "INSERT INTO ai_job_costs(job_id,owner_id,budget_day,currency,provider_label,model,price_version,price_date,input_per_million,output_per_million,reserved) VALUES(#{job},#{owner},#{day},#{p.currency},#{p.provider},#{p.model},#{p.version},#{p.date},#{p.input},#{p.output},#{p.reserve})")
    void insert(UUID owner, UUID job, LocalDate day, Price p);

    @Select(
        "SELECT job_id,currency,provider_label,model,price_version,price_date,input_per_million,output_per_million,reserved,charged,source,budget_day FROM ai_job_costs WHERE owner_id=#{owner} AND job_id=#{job}")
    Cost cost(UUID owner, UUID job);

    @Update(
        "UPDATE ai_money_days SET reserved=reserved-#{c.reserved},consumed=consumed+#{charged} WHERE owner_id=#{owner} AND budget_day=#{c.budgetDay} AND currency=#{c.currency}")
    void settleDay(UUID owner, Cost c, BigDecimal charged);

    @Update(
        "UPDATE ai_job_costs SET charged=#{charged},source=#{source} WHERE owner_id=#{owner} AND job_id=#{job}")
    void finish(UUID owner, UUID job, BigDecimal charged, String source);
  }

  private final Store db;
  private final Price policy;
  private final dev.scarletnexus.skill.CatalogMapper catalog;

  public MoneyLedger(Store db, Environment env, dev.scarletnexus.skill.CatalogMapper catalog) {
    this.catalog = catalog;
    this.db = db;
    boolean enabled = env.getProperty("app.ai.budget.enabled", Boolean.class, false);
    if (env.getProperty("app.ai.enabled", Boolean.class, false)
        && env.getProperty("app.ai.retries", Integer.class, 0) != 0)
      throw new IllegalArgumentException(
          "Monetary generation requires retries=0 until per-attempt billing exists");
    if (env.getProperty("app.ai.enabled", Boolean.class, false) && !enabled)
      throw new IllegalArgumentException(
          "AI monetary budget must be configured before enabling provider");
    policy =
        enabled
            ? new Price(
                required(env, "currency"),
                required(env, "provider-label"),
                env.getRequiredProperty("app.ai.model"),
                required(env, "price-version"),
                LocalDate.parse(required(env, "price-date")),
                new BigDecimal(required(env, "input-per-million")),
                new BigDecimal(required(env, "output-per-million")),
                new BigDecimal(required(env, "per-job-reserve")),
                new BigDecimal(required(env, "daily-limit")))
            : null;
  }

  private static String required(Environment env, String name) {
    return env.getRequiredProperty("app.ai.budget." + name);
  }

  @Transactional(propagation = Propagation.SUPPORTS)
  public Price configuredPrice() {
    DomainFailure.require(policy != null, "AI_MONEY_POLICY_REQUIRED", "请先配置服务端货币预算");
    return policy;
  }

  @Transactional(propagation = Propagation.SUPPORTS)
  public void validateEstimate(long input, long output) {
    DomainFailure.require(policy != null, "AI_MONEY_POLICY_REQUIRED", "请先配置服务端货币预算");
    DomainFailure.require(
        policy.reserve().compareTo(policy.calculate(input, output)) >= 0,
        "AI_RESERVE_TOO_SMALL",
        "货币预留不足以覆盖本次估计用量");
  }

  public void reserve(UUID owner, UUID job, LocalDate day) {
    if (policy != null) reserve(owner, job, day, policy);
  }

  // Caller already holds the owner row lock; custom policies are internal/test only.
  public void reserve(UUID owner, UUID job, LocalDate day, Price price) {
    catalog.lockOwner(owner);
    db.day(owner, day, price.currency());
    DomainFailure.require(
        db.reserve(owner, day, price) == 1, "AI_MONEY_BUDGET_EXCEEDED", "当日货币预算不足");
    db.insert(owner, job, day, price);
  }

  public void settle(UUID owner, UUID job, AIProvider.Usage usage, boolean notSent) {
    catalog.lockOwner(owner);
    Cost c = db.cost(owner, job);
    if (c == null || c.charged() != null) return;
    BigDecimal charged = c.reserved();
    String source = "UNKNOWN";
    if (notSent) {
      charged = BigDecimal.ZERO;
      source = "NOT_SENT";
    } else if (usage != null
        && usage.inputTokens() != null
        && usage.outputTokens() != null
        && usage.inputTokens() >= 0
        && usage.outputTokens() >= 0
        && usage.inputTokens() <= 1_000_000_000L
        && usage.outputTokens() <= 1_000_000_000L) {
      charged =
          new Price(
                  c.currency(),
                  c.providerLabel(),
                  c.model(),
                  c.priceVersion(),
                  c.priceDate(),
                  c.inputPerMillion(),
                  c.outputPerMillion(),
                  c.reserved(),
                  c.reserved())
              .calculate(usage.inputTokens(), usage.outputTokens());
      source = "PRICED_USAGE";
    }
    db.settleDay(owner, c, charged);
    db.finish(owner, job, charged, source);
  }
}
