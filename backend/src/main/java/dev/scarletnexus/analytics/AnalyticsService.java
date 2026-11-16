package dev.scarletnexus.analytics;

import static dev.scarletnexus.common.DomainFailure.require;

import dev.scarletnexus.analytics.AnalyticsModels.*;
import dev.scarletnexus.identity.CurrentUser;
import dev.scarletnexus.learning.LearningModels.Page;
import dev.scarletnexus.planner.PlanningSupport;
import dev.scarletnexus.skill.CatalogMapper;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class AnalyticsService {
  private final AnalyticsMapper db;
  private final CatalogMapper catalog;
  private final PlanningSupport support;
  private final dev.scarletnexus.assessment.PracticeMapper practice;

  public AnalyticsService(
      AnalyticsMapper db,
      CatalogMapper catalog,
      PlanningSupport support,
      dev.scarletnexus.assessment.PracticeMapper practice) {
    this.db = db;
    this.catalog = catalog;
    this.support = support;
    this.practice = practice;
  }

  private record Range(LocalDate from, LocalDate to, String zone, Instant start, Instant end) {}

  public record EvidenceTrace(
      dev.scarletnexus.assessment.PracticeModels.Evidence evidence, UUID taskId) {}

  public EvidenceTrace evidence(UUID id) {
    var e = support.found(practice.evidence(CurrentUser.id(), id));
    return new EvidenceTrace(
        e, support.found(practice.attempt(CurrentUser.id(), e.attemptId())).taskId());
  }

  private Range range(LocalDate from, LocalDate to, String zone) {
    var z = support.zone(zone);
    LocalDate last = to == null ? LocalDate.now(z) : to,
        first = from == null ? last.minusDays(29) : from;
    require(
        first.getYear() >= 2000
            && last.getYear() <= 2200
            && !first.isAfter(last)
            && ChronoUnit.DAYS.between(first, last) < 366,
        "INVALID_ANALYTICS_RANGE",
        "请选择2000至2200年内不超过366天的日期范围");
    return new Range(
        first,
        last,
        zone,
        first.atStartOfDay(z).toInstant(),
        last.plusDays(1).atStartOfDay(z).toInstant());
  }

  private void skill(UUID id) {
    if (id != null) support.found(catalog.skill(CurrentUser.id(), id));
  }

  private Totals total(List<Tasks> tasks, List<Time> time, List<Attempts> attempts) {
    long count = tasks.stream().mapToLong(Tasks::total).sum(),
        cancelled = tasks.stream().mapToLong(Tasks::cancelled).sum();
    return new Totals(
        time.stream().mapToLong(Time::milliseconds).sum(),
        count,
        tasks.stream().mapToLong(Tasks::done).sum(),
        cancelled,
        count - cancelled,
        attempts.stream().mapToLong(Attempts::total).sum(),
        attempts.stream().mapToLong(Attempts::independent).sum(),
        attempts.stream().mapToLong(Attempts::judged).sum(),
        attempts.stream().mapToLong(Attempts::correct).sum(),
        attempts.stream().mapToLong(Attempts::unsure).sum());
  }

  public Report report(LocalDate from, LocalDate to, String zone, UUID skill) {
    skill(skill);
    var r = range(from, to, zone);
    var owner = CurrentUser.id();
    var tasks = db.tasks(owner, r.start(), r.end(), skill);
    var time = db.time(owner, r.start(), r.end(), zone, skill);
    var attempts = db.attempts(owner, r.start(), r.end(), zone, skill);
    var skills =
        skill == null
            ? catalog.skills(owner, "", "ALL", 1001, 0)
            : List.of(catalog.skill(owner, skill));
    require(skills.size() <= 1000, "ANALYTICS_SCOPE_TOO_LARGE", "技能超过1000项，请选择单个技能");
    var rows =
        skills.stream()
            .map(
                s ->
                    new Skill(
                        s.id(),
                        s.name(),
                        s.status(),
                        total(
                            tasks.stream().filter(t -> t.skillId().equals(s.id())).toList(),
                            time.stream().filter(t -> t.skillId().equals(s.id())).toList(),
                            attempts.stream().filter(t -> t.skillId().equals(s.id())).toList())))
            .toList();
    var days = new ArrayList<Day>();
    for (LocalDate day = r.from(); !day.isAfter(r.to()); day = day.plusDays(1)) {
      LocalDate d = day;
      var daily =
          total(
              List.of(),
              time.stream().filter(t -> t.day().equals(d)).toList(),
              attempts.stream().filter(a -> a.day().equals(d)).toList());
      days.add(
          new Day(
              d,
              daily.studyMilliseconds(),
              daily.attempts(),
              daily.independentAttempts(),
              daily.judgedIndependentAttempts(),
              daily.correctIndependentAttempts()));
    }
    return new Report(
        r.from(),
        r.to(),
        zone,
        skill,
        Instant.now(),
        total(tasks, time, attempts),
        rows,
        days,
        db.routes(owner, skill),
        List.of(
            "时长仅计已结束时段，按结束日期归入，不按跨日逐分钟拆分；进行中/暂停未结束不计。",
            "任务按范围内创建、当前状态统计；取消单列且不进完成率分母。",
            "每作答只取结束日期前最新RULE比对；独立正确率分母仅独立且明确对错，UNSURE/无规则复核单列，自评不计。",
            "路线是当前ACTIVE选择的必修自报完成，与能力无等号；能力按结束日期前最近RULE/SELF分别列出。"));
  }

  public Page<AssessmentView> assessments(
      LocalDate to, String zone, UUID skill, int page, int size) {
    skill(skill);
    support.page(page, size);
    var r = range(null, to, zone);
    return new Page<>(
        db.assessments(CurrentUser.id(), skill, r.end(), size, page * size).stream()
            .map(a -> new AssessmentView(a, db.evidence(CurrentUser.id(), a.id())))
            .toList(),
        db.assessmentCount(CurrentUser.id(), skill, r.end()),
        page,
        size);
  }
}
