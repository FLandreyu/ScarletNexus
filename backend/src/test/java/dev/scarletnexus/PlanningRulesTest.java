package dev.scarletnexus;

import static org.assertj.core.api.Assertions.*;

import dev.scarletnexus.planner.*;
import dev.scarletnexus.planner.PlannerModels.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class PlanningRulesTest {
  Candidate candidate(UUID skill, String kind, int minutes, int priority, boolean eligible) {
    UUID id = UUID.randomUUID();
    return new Candidate(
        kind,
        id,
        kind.equals("LEARN") ? id : null,
        kind.equals("REVIEW") ? id : null,
        skill,
        UUID.randomUUID(),
        "Item",
        minutes,
        0,
        0,
        eligible,
        "Reason",
        priority,
        null,
        false,
        0,
        Instant.EPOCH);
  }

  @Test
  void reviewFirstRoundRobinAndWholeTaskBudget() {
    UUID a = UUID.randomUUID(), b = UUID.randomUUID(), c = UUID.randomUUID();
    var input =
        List.of(
            candidate(a, "LEARN", 20, 1, true),
            candidate(a, "LEARN", 20, 1, true),
            candidate(b, "LEARN", 20, 3, true),
            candidate(c, "REVIEW", 10, 5, true));
    var output =
        PlanningRules.generate(
            input,
            50,
            List.of(new SkillLimit(a, 50), new SkillLimit(b, 50), new SkillLimit(c, 50)));
    var chosen = output.stream().filter(Decision::selected).toList();
    assertThat(chosen).hasSize(3);
    assertThat(chosen.getFirst().candidate().kind()).isEqualTo("REVIEW");
    assertThat(chosen.stream().map(d -> d.candidate().skillId())).containsExactly(c, a, b);
    assertThat(chosen.stream().mapToInt(d -> d.candidate().minutes()).sum()).isEqualTo(50);
  }

  @Test
  void capsBlockedAndSmallerTaskStillFit() {
    UUID a = UUID.randomUUID();
    var output =
        PlanningRules.generate(
            List.of(
                candidate(a, "LEARN", 60, 1, true),
                candidate(a, "LEARN", 15, 2, true),
                candidate(a, "REVIEW", 5, 1, false)),
            20,
            List.of(new SkillLimit(a, 15)));
    assertThat(output.stream().filter(Decision::selected).map(d -> d.candidate().minutes()))
        .containsExactly(15);
    assertThat(output.stream().filter(d -> !d.selected())).hasSize(2);
    assertThat(output.stream().allMatch(d -> !d.reason().isBlank())).isTrue();
  }

  @Test
  void selectionCannotBypassCapsOrEligibility() {
    UUID a = UUID.randomUUID(), id = UUID.randomUUID();
    var item =
        new DraftItem(
            id, "LEARN", id, id, null, a, id, "Task", 25, 0, null, true, true, 0, "Reason");
    assertThatThrownBy(
            () -> PlanningRules.validate(List.of(item), 20, List.of(new SkillLimit(a, 100))))
        .hasMessageContaining("每日");
    assertThatThrownBy(
            () -> PlanningRules.validate(List.of(item), 100, List.of(new SkillLimit(a, 20))))
        .hasMessageContaining("技能");
    assertThatThrownBy(
            () -> PlanningRules.validate(List.of(item, item), 100, List.of(new SkillLimit(a, 100))))
        .hasMessageContaining("重复");
  }
}
