package dev.scarletnexus.planner;

import static dev.scarletnexus.common.DomainFailure.require;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.scarletnexus.common.DomainFailure;
import dev.scarletnexus.identity.CurrentUser;
import dev.scarletnexus.skill.CatalogMapper;
import java.time.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class PlanningSupport {
  private final CatalogMapper catalog;
  private final PlannerMapper db;
  private final ObjectMapper json;

  public PlanningSupport(CatalogMapper catalog, PlannerMapper db, ObjectMapper json) {
    this.catalog = catalog;
    this.db = db;
    this.json = json;
  }

  public UUID owner() {
    return CurrentUser.id();
  }

  public void lock() {
    found(catalog.lockOwner(owner()));
  }

  public <T> T found(T v) {
    if (v == null) throw DomainFailure.missing();
    return v;
  }

  public void conflict(boolean valid) {
    if (!valid)
      throw new DomainFailure(HttpStatus.CONFLICT, "PLANNING_CONFLICT", "计划或源记录已变化，请刷新并重新生成草案");
  }

  public void key(String key) {
    require(
        key != null && key.matches("[A-Za-z0-9_-]{1,100}"), "INVALID_IDEMPOTENCY_KEY", "操作键格式无效");
  }

  public String hash(Object value) {
    try {
      return dev.scarletnexus.assessment.PracticeService.hash(json.writeValueAsString(value));
    } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
      throw new IllegalStateException(e);
    }
  }

  public void page(int page, int size) {
    require(page >= 0 && page <= 100000 && size > 0 && size <= 100, "INVALID_QUERY", "分页参数无效");
  }

  public ZoneId zone(String value) {
    try {
      var zone = ZoneId.of(value);
      require(db.validZone(value), "INVALID_TIME_ZONE", "请使用有效IANA时区");
      return zone;
    } catch (DateTimeException e) {
      throw new DomainFailure(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_TIME_ZONE", "请使用有效IANA时区");
    }
  }

  public Instant due(LocalDate date, String zone) {
    return date.atTime(9, 0).atZone(zone(zone)).toInstant();
  }

  public void idle(UUID task, dev.scarletnexus.learning.LearningMapper learning) {
    var open = learning.openSession(owner());
    require(open == null || !open.taskId().equals(task), "TASK_SESSION_OPEN", "请先结束该任务的计时");
  }
}
