package dev.scarletnexus.planner;

import dev.scarletnexus.planner.PlannerModels.Adjustment;
import java.time.LocalDate;
import java.util.*;
import org.apache.ibatis.annotations.*;

@Mapper
public interface AdjustmentMapper {
  String C =
      "id,task_id,kind,input_hash,previous_due,next_due,previous_status,next_status,previous_revision,next_revision,reason,created_at";

  @Select("SELECT " + C + " FROM task_adjustments WHERE owner_id=#{owner} AND request_key=#{key}")
  Adjustment replay(UUID owner, String key);

  @Select("SELECT " + C + " FROM task_adjustments WHERE owner_id=#{owner} AND id=#{id}")
  Adjustment get(UUID owner, UUID id);

  @Insert(
      "INSERT INTO task_adjustments(id,owner_id,task_id,kind,request_key,input_hash,previous_due,next_due,previous_status,next_status,previous_revision,next_revision,reason) VALUES(#{id},#{owner},#{task},#{kind},#{key},#{hash},#{before},#{after},#{oldStatus},#{status},#{oldRevision},#{revision},#{reason})")
  void insert(
      UUID owner,
      UUID id,
      UUID task,
      String kind,
      String key,
      String hash,
      LocalDate before,
      LocalDate after,
      String oldStatus,
      String status,
      int oldRevision,
      int revision,
      String reason);

  @Insert(
      "INSERT INTO task_splits(owner_id,adjustment_id,parent_task_id,child_task_id) VALUES(#{owner},#{id},#{parent},#{child})")
  void split(UUID owner, UUID id, UUID parent, UUID child);

  @Select(
      "SELECT child_task_id FROM task_splits WHERE owner_id=#{owner} AND adjustment_id=#{id} ORDER BY child_task_id")
  List<UUID> children(UUID owner, UUID id);

  @Select(
      "SELECT "
          + C
          + " FROM task_adjustments WHERE owner_id=#{owner} AND task_id=#{task} ORDER BY created_at DESC,id LIMIT #{size} OFFSET #{offset}")
  List<Adjustment> history(UUID owner, UUID task, int size, int offset);

  @Select("SELECT count(*) FROM task_adjustments WHERE owner_id=#{owner} AND task_id=#{task}")
  long count(UUID owner, UUID task);

  @Select(
      "SELECT EXISTS(SELECT 1 FROM review_cycle_tasks WHERE owner_id=#{owner} AND task_id=#{task})")
  boolean reviewTask(UUID owner, UUID task);
}
