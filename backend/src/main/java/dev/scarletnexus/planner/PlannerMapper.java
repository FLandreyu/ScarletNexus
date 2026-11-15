package dev.scarletnexus.planner;

import dev.scarletnexus.planner.PlannerModels.*;
import java.time.*;
import java.util.*;
import org.apache.ibatis.annotations.*;

@Mapper
public interface PlannerMapper {
  @Select("SELECT EXISTS(SELECT 1 FROM pg_timezone_names WHERE name=#{zone})")
  boolean validZone(String zone);

  @Select(
      "<script>SELECT t.id,t.skill_id,t.goal_id,t.node_id,t.title,t.planned_minutes,t.due_date,t.status,t.revision,s.status AS skill_status,n.status AS node_status,g.status AS goal_status,COALESCE(g.priority,5) AS priority,g.deadline AS goal_deadline,(SELECT e.outcome FROM learning_evidence e WHERE e.owner_id=t.owner_id AND e.node_id=t.node_id ORDER BY e.observed_at DESC,e.id DESC LIMIT 1) AS latest_outcome,t.created_at FROM learning_tasks t JOIN skills s ON s.owner_id=t.owner_id AND s.id=t.skill_id JOIN knowledge_nodes n ON n.owner_id=t.owner_id AND n.id=t.node_id LEFT JOIN learning_goals g ON g.owner_id=t.owner_id AND g.id=t.goal_id WHERE t.owner_id=#{owner} AND t.status NOT IN ('DONE','CANCELLED') AND NOT EXISTS(SELECT 1 FROM review_cycle_tasks r WHERE r.owner_id=t.owner_id AND r.task_id=t.id) AND t.skill_id IN <foreach collection='skills' item='skill' open='(' separator=',' close=')'>#{skill}</foreach> ORDER BY t.id LIMIT 2001</script>")
  List<TaskRow> candidates(UUID owner, List<UUID> skills);

  @Select(
      "SELECT node_id FROM learning_tasks WHERE owner_id=#{owner} AND status='DONE' UNION SELECT n.node_id FROM learning_progress p JOIN learning_path_nodes n ON n.owner_id=p.owner_id AND n.id=p.entry_id WHERE p.owner_id=#{owner} AND p.status='COMPLETED' ORDER BY node_id")
  List<UUID> doneNodes(UUID owner);

  @Insert(
      "INSERT INTO plan_drafts(id,owner_id,plan_date,time_zone,budget_minutes,source_hash,state) VALUES(#{id},#{owner},#{date},#{zone},#{budget},#{hash},'DRAFT')")
  void draftInsert(UUID owner, UUID id, LocalDate date, String zone, int budget, String hash);

  @Select(
      "SELECT id,plan_date,time_zone,budget_minutes,source_hash,revision,state,created_at FROM plan_drafts WHERE owner_id=#{owner} AND id=#{id}")
  Draft draft(UUID owner, UUID id);

  @Select(
      "SELECT id,plan_date,time_zone,budget_minutes,source_hash,revision,state,created_at FROM plan_drafts WHERE owner_id=#{owner} AND plan_date=#{date} ORDER BY created_at DESC,id LIMIT #{size} OFFSET #{offset}")
  List<Draft> drafts(UUID owner, LocalDate date, int size, int offset);

  @Select("SELECT count(*) FROM plan_drafts WHERE owner_id=#{owner} AND plan_date=#{date}")
  long draftCount(UUID owner, LocalDate date);

  @Insert(
      "INSERT INTO plan_draft_limits(owner_id,draft_id,skill_id,max_minutes) VALUES(#{owner},#{draft},#{limit.skillId},#{limit.maxMinutes})")
  void draftLimit(UUID owner, UUID draft, SkillLimit limit);

  @Select(
      "SELECT skill_id,max_minutes FROM plan_draft_limits WHERE owner_id=#{owner} AND draft_id=#{draft} ORDER BY skill_id")
  List<SkillLimit> draftLimits(UUID owner, UUID draft);

  @Insert(
      "INSERT INTO plan_draft_items(id,owner_id,draft_id,kind,task_id,review_id,skill_id,node_id,title,minutes,task_revision,review_revision,eligible,selected,display_order,reason) VALUES(#{id},#{owner},#{draft},#{c.kind},#{c.taskId},#{c.reviewId},#{c.skillId},#{c.nodeId},#{c.title},#{c.minutes},#{c.taskRevision},#{c.reviewRevision},#{c.eligible},#{selected},#{order},#{reason})")
  void draftItem(
      UUID owner, UUID id, UUID draft, Candidate c, boolean selected, int order, String reason);

  @Select(
      "SELECT id,kind,source_id,task_id,review_id,skill_id,node_id,title,minutes,task_revision,review_revision,eligible,selected,display_order,reason FROM plan_draft_items WHERE owner_id=#{owner} AND draft_id=#{draft} ORDER BY display_order,id")
  List<DraftItem> draftItems(UUID owner, UUID draft);

  @Update(
      "UPDATE plan_draft_items SET selected=#{selected},display_order=#{order},reason=#{reason} WHERE owner_id=#{owner} AND id=#{id}")
  void chooseItem(UUID owner, UUID id, boolean selected, int order, String reason);

  @Update(
      "UPDATE plan_drafts SET revision=revision+1,state=#{state},updated_at=now() WHERE owner_id=#{owner} AND id=#{id}")
  void draftState(UUID owner, UUID id, String state);

  @Select(
      "SELECT active_plan_id,revision FROM daily_plan_days WHERE owner_id=#{owner} AND plan_date=#{date}")
  Day day(UUID owner, LocalDate date);

  @Insert(
      "INSERT INTO daily_plan_days(id,owner_id,plan_date) VALUES(#{id},#{owner},#{date}) ON CONFLICT(owner_id,plan_date) DO NOTHING")
  void dayInsert(UUID owner, UUID id, LocalDate date);

  @Update(
      "UPDATE daily_plan_days SET active_plan_id=#{plan},revision=revision+1 WHERE owner_id=#{owner} AND plan_date=#{date}")
  void daySet(UUID owner, LocalDate date, UUID plan);

  @Select(
      "SELECT COALESCE(max(version_number),0)+1 FROM daily_plans WHERE owner_id=#{owner} AND plan_date=#{date}")
  int nextVersion(UUID owner, LocalDate date);

  @Insert(
      "INSERT INTO daily_plans(id,owner_id,draft_id,plan_date,time_zone,version_number,budget_minutes) VALUES(#{id},#{owner},#{draft.id},#{draft.planDate},#{draft.timeZone},#{version},#{draft.budgetMinutes})")
  void planInsert(UUID owner, UUID id, Draft draft, int version);

  @Insert(
      "INSERT INTO daily_plan_limits(owner_id,plan_id,skill_id,max_minutes) VALUES(#{owner},#{plan},#{limit.skillId},#{limit.maxMinutes})")
  void planLimit(UUID owner, UUID plan, SkillLimit limit);

  @Insert(
      "INSERT INTO daily_plan_items(id,owner_id,plan_id,task_id,review_id,skill_id,title_snapshot,planned_minutes,task_revision,display_order,reason) VALUES(#{id},#{owner},#{plan},#{task},#{item.reviewId},#{item.skillId},#{item.title},#{item.minutes},#{revision},#{order},#{item.reason})")
  void planItem(UUID owner, UUID id, UUID plan, UUID task, DraftItem item, int revision, int order);

  @Select(
      "SELECT id,draft_id,plan_date,time_zone,version_number,budget_minutes,confirmed_at FROM daily_plans WHERE owner_id=#{owner} AND id=#{id}")
  Plan plan(UUID owner, UUID id);

  @Select(
      "SELECT id,draft_id,plan_date,time_zone,version_number,budget_minutes,confirmed_at FROM daily_plans WHERE owner_id=#{owner} AND plan_date=#{date} ORDER BY version_number DESC LIMIT #{size} OFFSET #{offset}")
  List<Plan> plans(UUID owner, LocalDate date, int size, int offset);

  @Select("SELECT count(*) FROM daily_plans WHERE owner_id=#{owner} AND plan_date=#{date}")
  long planCount(UUID owner, LocalDate date);

  @Select(
      "SELECT skill_id,max_minutes FROM daily_plan_limits WHERE owner_id=#{owner} AND plan_id=#{plan} ORDER BY skill_id")
  List<SkillLimit> planLimits(UUID owner, UUID plan);

  @Select(
      "SELECT id,task_id,review_id,skill_id,title_snapshot,planned_minutes,task_revision,display_order,reason FROM daily_plan_items WHERE owner_id=#{owner} AND plan_id=#{plan} ORDER BY display_order,id")
  List<PlanItem> planItems(UUID owner, UUID plan);

  @Select(
      "SELECT plan_id,input_hash FROM plan_confirmation_keys WHERE owner_id=#{owner} AND request_key=#{key}")
  Key confirmation(UUID owner, String key);

  @Insert(
      "INSERT INTO plan_confirmation_keys(owner_id,request_key,input_hash,plan_id) VALUES(#{owner},#{key},#{hash},#{plan})")
  void confirmationInsert(UUID owner, String key, String hash, UUID plan);

  @Select(
      "SELECT EXISTS(SELECT 1 FROM daily_plan_items i JOIN daily_plans p ON p.owner_id=i.owner_id AND p.id=i.plan_id JOIN daily_plan_days d ON d.owner_id=p.owner_id AND d.active_plan_id=p.id WHERE i.owner_id=#{owner} AND i.task_id=#{task} AND p.plan_date >= (now() AT TIME ZONE p.time_zone)::date)")
  boolean futurePlanned(UUID owner, UUID task);
}
