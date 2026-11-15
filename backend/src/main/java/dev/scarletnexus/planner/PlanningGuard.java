package dev.scarletnexus.planner;

import static dev.scarletnexus.common.DomainFailure.require;

import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class PlanningGuard {
  private final PlannerMapper db;

  public PlanningGuard(PlannerMapper db) {
    this.db = db;
  }

  public void minutes(UUID owner, UUID task, int before, int after) {
    require(
        before == after || !db.futurePlanned(owner, task),
        "TASK_IN_CONFIRMED_PLAN",
        "任务属于当前或未来确认计划，请先取消对应计划，再调整预计时长");
  }
}
