package dev.scarletnexus.learning;

import dev.scarletnexus.learning.LearningModels.*;
import java.time.*;
import java.util.*;
import org.apache.ibatis.annotations.*;

@Mapper
public interface LearningMapper {
  @Select(
      "SELECT id,skill_id,title,target_description,deadline,priority,status,revision FROM learning_goals WHERE owner_id=#{owner} ORDER BY updated_at DESC,id LIMIT #{size} OFFSET #{offset}")
  List<Goal> goals(UUID owner, int size, int offset);

  @Select("SELECT count(*) FROM learning_goals WHERE owner_id=#{owner}")
  long goalCount(UUID owner);

  @Select(
      "SELECT id,skill_id,title,target_description,deadline,priority,status,revision FROM learning_goals WHERE owner_id=#{owner} AND id=#{id}")
  Goal goal(UUID owner, UUID id);

  @Insert(
      "INSERT INTO learning_goals(id,owner_id,skill_id,title,target_description,deadline,priority,status) VALUES(#{id},#{owner},#{skill},#{title},#{description},#{deadline},#{priority},#{status})")
  void insertGoal(
      UUID owner,
      UUID id,
      UUID skill,
      String title,
      String description,
      LocalDate deadline,
      int priority,
      String status);

  @Update(
      "UPDATE learning_goals SET title=#{title},target_description=#{description},deadline=#{deadline},priority=#{priority},status=#{status},revision=revision+1,updated_at=now() WHERE owner_id=#{owner} AND id=#{id}")
  void updateGoal(
      UUID owner,
      UUID id,
      String title,
      String description,
      LocalDate deadline,
      int priority,
      String status);

  @Select(
      "SELECT id,skill_id,goal_id,family_id,version_number,previous_version_id,title,created_at FROM learning_paths WHERE owner_id=#{owner} ORDER BY created_at DESC,id LIMIT #{size} OFFSET #{offset}")
  List<PathVersion> paths(UUID owner, int size, int offset);

  @Select("SELECT count(*) FROM learning_paths WHERE owner_id=#{owner}")
  long pathCount(UUID owner);

  @Select(
      "SELECT id,skill_id,goal_id,family_id,version_number,previous_version_id,title,created_at FROM learning_paths WHERE owner_id=#{owner} AND id=#{id}")
  PathVersion path(UUID owner, UUID id);

  @Select(
      "SELECT max(version_number) FROM learning_paths WHERE owner_id=#{owner} AND family_id=#{family}")
  int latestVersion(UUID owner, UUID family);

  @Insert(
      "INSERT INTO learning_paths(id,owner_id,skill_id,goal_id,family_id,version_number,previous_version_id,title) VALUES(#{id},#{owner},#{skill},#{goal},#{family},#{version},#{previous},#{title})")
  void insertPath(
      UUID owner,
      UUID id,
      UUID skill,
      UUID goal,
      UUID family,
      int version,
      UUID previous,
      String title);

  @Insert(
      "INSERT INTO learning_path_stages(id,owner_id,path_id,title,display_order) VALUES(#{id},#{owner},#{path},#{title},#{order})")
  void insertStage(UUID owner, UUID id, UUID path, String title, int order);

  @Insert(
      "INSERT INTO learning_path_nodes(id,owner_id,path_id,stage_id,node_id,title_snapshot,display_order,target_level,required) VALUES(#{id},#{owner},#{path},#{stage},#{node},#{title},#{order},#{level},#{required})")
  void insertEntry(
      UUID owner,
      UUID id,
      UUID path,
      UUID stage,
      UUID node,
      String title,
      int order,
      int level,
      boolean required);

  @Select(
      "SELECT id,path_id,title,display_order FROM learning_path_stages WHERE owner_id=#{owner} AND path_id=#{path} ORDER BY display_order,id")
  List<Stage> stages(UUID owner, UUID path);

  @Select(
      "SELECT e.id,e.path_id,e.stage_id,e.node_id,e.title_snapshot,e.display_order,e.target_level,e.required FROM learning_path_nodes e JOIN learning_path_stages s ON s.id=e.stage_id AND s.owner_id=e.owner_id WHERE e.owner_id=#{owner} AND e.path_id=#{path} ORDER BY s.display_order,e.display_order,e.id")
  List<Entry> entries(UUID owner, UUID path);

  @Select(
      "SELECT id,path_id,stage_id,node_id,title_snapshot,display_order,target_level,required FROM learning_path_nodes WHERE owner_id=#{owner} AND id=#{id}")
  Entry entry(UUID owner, UUID id);

  @Select(
      "SELECT id,path_id,status,started_at FROM path_enrollments WHERE owner_id=#{owner} ORDER BY updated_at DESC,id LIMIT #{size} OFFSET #{offset}")
  List<Enrollment> enrollments(UUID owner, int size, int offset);

  @Select("SELECT count(*) FROM path_enrollments WHERE owner_id=#{owner}")
  long enrollmentCount(UUID owner);

  @Select(
      "SELECT id,path_id,status,started_at FROM path_enrollments WHERE owner_id=#{owner} AND id=#{id}")
  Enrollment enrollment(UUID owner, UUID id);

  @Select(
      "SELECT id,path_id,status,started_at FROM path_enrollments WHERE owner_id=#{owner} AND path_id=#{path}")
  Enrollment enrolled(UUID owner, UUID path);

  @Select(
      "SELECT id,path_id,status,started_at FROM path_enrollments WHERE owner_id=#{owner} AND status='ACTIVE'")
  Enrollment activeEnrollment(UUID owner);

  @Update(
      "UPDATE path_enrollments SET status='PAUSED',updated_at=now() WHERE owner_id=#{owner} AND status='ACTIVE'")
  void pauseEnrollments(UUID owner);

  @Update(
      "UPDATE path_enrollments SET status=#{status},updated_at=now() WHERE owner_id=#{owner} AND id=#{id}")
  void enrollmentState(UUID owner, UUID id, String status);

  @Insert(
      "INSERT INTO path_enrollments(id,owner_id,path_id,status) VALUES(#{id},#{owner},#{path},'ACTIVE')")
  void insertEnrollment(UUID owner, UUID id, UUID path);

  @Select(
      "SELECT entry_id,status,revision FROM learning_progress WHERE owner_id=#{owner} AND enrollment_id=#{enrollment}")
  List<Progress> progress(UUID owner, UUID enrollment);

  @Select(
      "SELECT entry_id,status,revision FROM learning_progress WHERE owner_id=#{owner} AND enrollment_id=#{enrollment} AND entry_id=#{entry}")
  Progress progressOne(UUID owner, UUID enrollment, UUID entry);

  @Insert(
      "INSERT INTO learning_progress(id,owner_id,enrollment_id,path_id,entry_id,status,revision) VALUES(#{id},#{owner},#{enrollment},#{path},#{entry},#{status},1) ON CONFLICT(owner_id,enrollment_id,entry_id) DO UPDATE SET status=EXCLUDED.status,revision=learning_progress.revision+1,updated_at=now()")
  void saveProgress(UUID owner, UUID id, UUID enrollment, UUID path, UUID entry, String status);

  @Select(
      "SELECT id,skill_id,goal_id,node_id,enrollment_id,entry_id,path_id,title,planned_minutes,due_date,status,revision FROM learning_tasks WHERE owner_id=#{owner} ORDER BY updated_at DESC,id LIMIT #{size} OFFSET #{offset}")
  List<Task> tasks(UUID owner, int size, int offset);

  @Select("SELECT count(*) FROM learning_tasks WHERE owner_id=#{owner}")
  long taskCount(UUID owner);

  @Select(
      "SELECT id,skill_id,goal_id,node_id,enrollment_id,entry_id,path_id,title,planned_minutes,due_date,status,revision FROM learning_tasks WHERE owner_id=#{owner} AND status IN ('DOING','TODO') ORDER BY CASE WHEN status='DOING' THEN 0 ELSE 1 END,updated_at DESC,id LIMIT 1")
  Task continuationTask(UUID owner);

  @Select(
      "SELECT id,skill_id,goal_id,node_id,enrollment_id,entry_id,path_id,title,planned_minutes,due_date,status,revision FROM learning_tasks WHERE owner_id=#{owner} AND id=#{id}")
  Task task(UUID owner, UUID id);

  @Insert(
      "INSERT INTO learning_tasks(id,owner_id,skill_id,goal_id,node_id,enrollment_id,entry_id,path_id,title,planned_minutes,due_date,status) VALUES(#{id},#{owner},#{skill},#{goal},#{node},#{enrollment},#{entry},#{path},#{title},#{minutes},#{due},'TODO')")
  void insertTask(
      UUID owner,
      UUID id,
      UUID skill,
      UUID goal,
      UUID node,
      UUID enrollment,
      UUID entry,
      UUID path,
      String title,
      int minutes,
      LocalDate due);

  @Update(
      "UPDATE learning_tasks SET title=#{title},planned_minutes=#{minutes},due_date=#{due},status=#{status},revision=revision+1,updated_at=now() WHERE owner_id=#{owner} AND id=#{id}")
  void updateTask(UUID owner, UUID id, String title, int minutes, LocalDate due, String status);

  @Select(
      "SELECT id,task_id,state,started_at,segment_started_at,ended_at,accumulated_millis,revision FROM learning_sessions WHERE owner_id=#{owner} AND id=#{id}")
  Session session(UUID owner, UUID id);

  @Select(
      "SELECT id,task_id,state,started_at,segment_started_at,ended_at,accumulated_millis,revision FROM learning_sessions WHERE owner_id=#{owner} AND state<>'ENDED'")
  Session openSession(UUID owner);

  @Select(
      "SELECT id,task_id,state,started_at,segment_started_at,ended_at,accumulated_millis,revision FROM learning_sessions WHERE owner_id=#{owner} AND task_id=#{task} ORDER BY started_at DESC,id LIMIT #{size} OFFSET #{offset}")
  List<Session> sessions(UUID owner, UUID task, int size, int offset);

  @Select("SELECT count(*) FROM learning_sessions WHERE owner_id=#{owner} AND task_id=#{task}")
  long sessionCount(UUID owner, UUID task);

  @Insert(
      "INSERT INTO learning_sessions(id,owner_id,task_id,state,started_at,segment_started_at) VALUES(#{id},#{owner},#{task},'RUNNING',#{now},#{now})")
  void insertSession(UUID owner, UUID id, UUID task, Instant now);

  @Update(
      "UPDATE learning_sessions SET state=#{state},segment_started_at=#{segment},ended_at=#{ended},accumulated_millis=#{millis},revision=revision+1 WHERE owner_id=#{owner} AND id=#{id}")
  void updateSession(
      UUID owner, UUID id, String state, Instant segment, Instant ended, long millis);

  @Select(
      "SELECT session_id,action,request_revision FROM learning_session_commands WHERE owner_id=#{owner} AND request_key=#{key}")
  Command command(UUID owner, String key);

  @Insert(
      "INSERT INTO learning_session_commands(id,owner_id,request_key,session_id,action,request_revision) VALUES(#{id},#{owner},#{key},#{session},#{action},#{revision})")
  void commandInsert(UUID owner, UUID id, String key, UUID session, String action, int revision);

  @Select(
      "SELECT id,task_id,body,revision,updated_at FROM learning_notes WHERE owner_id=#{owner} AND task_id=#{task}")
  Note note(UUID owner, UUID task);

  @Insert(
      "INSERT INTO learning_notes(id,owner_id,task_id,body,revision) VALUES(#{id},#{owner},#{task},#{body},1) ON CONFLICT(owner_id,task_id) DO UPDATE SET body=EXCLUDED.body,revision=learning_notes.revision+1,updated_at=now()")
  void saveNote(UUID owner, UUID id, UUID task, String body);

  @Select(
      "SELECT r.id,r.title,r.resource_type,r.url,r.status,r.revision FROM learning_resources r JOIN node_resources n ON n.owner_id=r.owner_id AND n.resource_id=r.id WHERE n.owner_id=#{owner} AND n.node_id=#{node} ORDER BY r.updated_at DESC,r.id LIMIT #{size} OFFSET #{offset}")
  List<Resource> resources(UUID owner, UUID node, int size, int offset);

  @Select("SELECT count(*) FROM node_resources WHERE owner_id=#{owner} AND node_id=#{node}")
  long resourceCount(UUID owner, UUID node);

  @Select(
      "SELECT id,title,resource_type,url,status,revision FROM learning_resources WHERE owner_id=#{owner} AND id=#{id}")
  Resource resource(UUID owner, UUID id);

  @Insert(
      "INSERT INTO learning_resources(id,owner_id,title,resource_type,url,status) VALUES(#{id},#{owner},#{title},#{type},#{url},'ACTIVE')")
  void insertResource(UUID owner, UUID id, String title, String type, String url);

  @Insert(
      "INSERT INTO node_resources(id,owner_id,node_id,resource_id) VALUES(#{id},#{owner},#{node},#{resource})")
  void attachResource(UUID owner, UUID id, UUID node, UUID resource);

  @Update(
      "UPDATE learning_resources SET title=#{title},resource_type=#{type},url=#{url},status=#{status},revision=revision+1,updated_at=now() WHERE owner_id=#{owner} AND id=#{id}")
  void updateResource(UUID owner, UUID id, String title, String type, String url, String status);
}
