package dev.scarletnexus.planner;

import dev.scarletnexus.planner.PlannerModels.*;
import java.time.*;
import java.util.*;
import org.apache.ibatis.annotations.*;

@Mapper
public interface ReviewMapper {
  String S =
      "id,skill_id,node_id,original_task_id,initial_evidence_id,planned_minutes,time_zone,next_review_at,interval_days,cycle_number,revision,status,reason,created_at,last_completed_at";

  @Select("SELECT " + S + " FROM review_schedules WHERE owner_id=#{owner} AND id=#{id}")
  Schedule schedule(UUID owner, UUID id);

  @Select("SELECT " + S + " FROM review_schedules WHERE owner_id=#{owner} AND node_id=#{node}")
  Schedule forNode(UUID owner, UUID node);

  @Select(
      "SELECT "
          + S
          + " FROM review_schedules WHERE owner_id=#{owner} AND (NOT #{due} OR (status='ACTIVE' AND next_review_at<=#{until})) ORDER BY next_review_at,id LIMIT #{size} OFFSET #{offset}")
  List<Schedule> schedules(UUID owner, boolean due, Instant until, int size, int offset);

  @Select(
      "SELECT count(*) FROM review_schedules WHERE owner_id=#{owner} AND (NOT #{due} OR (status='ACTIVE' AND next_review_at<=#{until}))")
  long count(UUID owner, boolean due, Instant until);

  @Insert(
      "INSERT INTO review_schedules(id,owner_id,skill_id,node_id,original_task_id,initial_evidence_id,planned_minutes,time_zone,next_review_at,interval_days,status,reason) VALUES(#{id},#{owner},#{skill},#{node},#{task},#{evidence},#{minutes},#{zone},#{due},#{interval},'ACTIVE',#{reason})")
  void insert(
      UUID owner,
      UUID id,
      UUID skill,
      UUID node,
      UUID task,
      UUID evidence,
      int minutes,
      String zone,
      Instant due,
      int interval,
      String reason);

  @Update(
      "UPDATE review_schedules SET next_review_at=#{due},status=#{status},reason=#{reason},revision=revision+1,updated_at=now() WHERE owner_id=#{owner} AND id=#{id}")
  void adjust(UUID owner, UUID id, Instant due, String status, String reason);

  @Update(
      "UPDATE review_schedules SET next_review_at=#{due},interval_days=#{interval},cycle_number=cycle_number+1,revision=revision+1,last_completed_at=#{completed},reason=#{reason},updated_at=now() WHERE owner_id=#{owner} AND id=#{id}")
  void complete(UUID owner, UUID id, Instant due, int interval, Instant completed, String reason);

  @Insert(
      "INSERT INTO review_events(id,owner_id,schedule_id,kind,request_key,input_hash,evidence_id,attempt_id,outcome,verification,independent,previous_due,next_due,previous_interval,next_interval,reason) VALUES(#{id},#{owner},#{schedule},#{kind},#{key},#{hash},#{evidence},#{attempt},#{outcome},#{verification},#{independent},#{before},#{after},#{previous},#{next},#{reason})")
  void event(
      UUID owner,
      UUID id,
      UUID schedule,
      String kind,
      String key,
      String hash,
      UUID evidence,
      UUID attempt,
      String outcome,
      String verification,
      Boolean independent,
      Instant before,
      Instant after,
      Integer previous,
      int next,
      String reason);

  String E =
      "id,schedule_id,kind,request_key,input_hash,evidence_id,attempt_id,outcome,verification,independent,previous_due,next_due,previous_interval,next_interval,reason,created_at";

  @Select("SELECT " + E + " FROM review_events WHERE owner_id=#{owner} AND request_key=#{key}")
  ReviewEvent replay(UUID owner, String key);

  @Select(
      "SELECT EXISTS(SELECT 1 FROM review_events WHERE owner_id=#{owner} AND evidence_id=#{evidence})")
  boolean used(UUID owner, UUID evidence);

  @Select(
      "SELECT "
          + E
          + " FROM review_events WHERE owner_id=#{owner} AND schedule_id=#{id} ORDER BY created_at DESC,id LIMIT #{size} OFFSET #{offset}")
  List<ReviewEvent> history(UUID owner, UUID id, int size, int offset);

  @Select("SELECT count(*) FROM review_events WHERE owner_id=#{owner} AND schedule_id=#{id}")
  long historyCount(UUID owner, UUID id);

  @Select(
      "SELECT task_id FROM review_cycle_tasks WHERE owner_id=#{owner} AND schedule_id=#{id} AND cycle_number=#{cycle}")
  UUID cycleTask(UUID owner, UUID id, int cycle);

  @Insert(
      "INSERT INTO review_cycle_tasks(owner_id,schedule_id,cycle_number,node_id,task_id) VALUES(#{owner},#{id},#{cycle},#{node},#{task})")
  void cycleTaskInsert(UUID owner, UUID id, int cycle, UUID node, UUID task);

  @Select(
      "<script>SELECT r.id,r.skill_id,r.node_id,n.title,r.planned_minutes,r.next_review_at,r.revision,r.cycle_number,r.status,s.status AS skill_status,n.status AS node_status,EXISTS(SELECT 1 FROM skill_nodes sn WHERE sn.owner_id=r.owner_id AND sn.skill_id=r.skill_id AND sn.node_id=r.node_id) AS attached,t.id AS task_id,t.revision AS task_revision,t.status AS task_status,r.created_at FROM review_schedules r JOIN skills s ON s.owner_id=r.owner_id AND s.id=r.skill_id JOIN knowledge_nodes n ON n.owner_id=r.owner_id AND n.id=r.node_id LEFT JOIN review_cycle_tasks c ON c.owner_id=r.owner_id AND c.schedule_id=r.id AND c.cycle_number=r.cycle_number LEFT JOIN learning_tasks t ON t.owner_id=c.owner_id AND t.id=c.task_id WHERE r.owner_id=#{owner} AND r.skill_id IN <foreach collection='skills' item='skill' open='(' separator=',' close=')'>#{skill}</foreach> ORDER BY r.id LIMIT 2001</script>")
  List<ReviewRow> candidates(UUID owner, List<UUID> skills);
}
