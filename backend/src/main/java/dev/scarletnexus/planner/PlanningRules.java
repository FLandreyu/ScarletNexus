package dev.scarletnexus.planner;

import static dev.scarletnexus.common.DomainFailure.require;

import dev.scarletnexus.planner.PlannerModels.*;
import java.time.LocalDate;
import java.util.*;

public final class PlanningRules {
  private PlanningRules() {}

  static final Comparator<Candidate> ORDER =
      Comparator.comparing(Candidate::continuing)
          .reversed()
          .thenComparingInt(Candidate::priority)
          .thenComparing(c -> c.deadline() == null ? LocalDate.MAX : c.deadline())
          .thenComparing(Comparator.comparingInt(Candidate::weakness).reversed())
          .thenComparing(Candidate::orderTime)
          .thenComparing(Candidate::key);

  public static List<Decision> generate(
      List<Candidate> candidates, int budget, List<SkillLimit> limits) {
    Map<UUID, Integer> caps = new HashMap<>(), used = new HashMap<>();
    for (var l : limits) caps.put(l.skillId(), l.maxMinutes());
    List<Decision> selected = new ArrayList<>(), excluded = new ArrayList<>();
    int total = 0;
    for (String kind : List.of("REVIEW", "LEARN")) {
      Map<UUID, ArrayDeque<Candidate>> groups = new HashMap<>();
      for (var c : candidates)
        if (c.kind().equals(kind)) {
          if (!c.eligible()) {
            excluded.add(new Decision(c, false, c.reason(), 0));
            continue;
          }
          groups.computeIfAbsent(c.skillId(), id -> new ArrayDeque<>()).add(c);
        }
      for (var q : groups.values()) {
        var sorted = new ArrayList<>(q);
        sorted.sort(ORDER);
        q.clear();
        q.addAll(sorted);
      }
      while (groups.values().stream().anyMatch(q -> !q.isEmpty())) {
        var round =
            groups.values().stream()
                .filter(q -> !q.isEmpty())
                .map(ArrayDeque::peek)
                .sorted(ORDER)
                .toList();
        for (var c : round) {
          groups.get(c.skillId()).remove();
          String why = null;
          if (selected.size() >= 200) why = "本版最多200项，请缩小规划范围";
          else if (c.minutes()
              > caps.getOrDefault(c.skillId(), 0) - used.getOrDefault(c.skillId(), 0))
            why = "超过该技能剩余预算，整项未选";
          else if (c.minutes() > budget - total) why = "超过当日剩余预算，整项未选";
          if (why != null) excluded.add(new Decision(c, false, why + "；" + c.reason(), 0));
          else {
            total += c.minutes();
            used.merge(c.skillId(), c.minutes(), Integer::sum);
            selected.add(
                new Decision(
                    c,
                    true,
                    "规则v1：" + c.reason() + "；跨技能轮选，本项" + c.minutes() + "分钟",
                    selected.size()));
          }
        }
      }
    }
    List<Decision> result = new ArrayList<>(selected);
    excluded.sort(Comparator.comparing(d -> d.candidate().key()));
    for (var d : excluded)
      result.add(new Decision(d.candidate(), false, d.reason(), result.size()));
    return List.copyOf(result);
  }

  public static int validate(List<DraftItem> items, int budget, List<SkillLimit> limits) {
    require(items.size() <= 200, "PLAN_TOO_MANY_ITEMS", "每天最多200项");
    var caps = new HashMap<UUID, Integer>();
    for (var l : limits) caps.put(l.skillId(), l.maxMinutes());
    var used = new HashMap<UUID, Integer>();
    Set<String> seen = new HashSet<>();
    int total = 0;
    for (var item : items) {
      require(seen.add(item.key()), "DUPLICATE_PLAN_ITEM", "计划项目不能重复");
      require(item.eligible(), "PLAN_ITEM_INELIGIBLE", "存在尚未满足先修或已不可用的项目");
      total += item.minutes();
      used.merge(item.skillId(), item.minutes(), Integer::sum);
      require(
          used.get(item.skillId()) <= caps.getOrDefault(item.skillId(), 0),
          "SKILL_TIME_BUDGET_EXCEEDED",
          "超过技能时间预算");
    }
    require(total <= budget, "DAILY_TIME_BUDGET_EXCEEDED", "超过每日时间预算");
    return total;
  }
}
