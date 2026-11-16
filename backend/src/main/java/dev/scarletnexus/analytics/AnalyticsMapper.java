package dev.scarletnexus.analytics;

import dev.scarletnexus.analytics.AnalyticsModels.*;
import java.time.*;
import java.util.*;
import org.apache.ibatis.annotations.*;

@Mapper
public interface AnalyticsMapper {
  String FILTER = "<if test='skill != null'> AND t.skill_id=#{skill}</if>";

  @Select(
      "<script>SELECT t.skill_id,count(*) AS total,count(*) FILTER(WHERE t.status='DONE') AS done,count(*) FILTER(WHERE t.status='CANCELLED') AS cancelled FROM learning_tasks t WHERE t.owner_id=#{owner} AND t.created_at>=#{start} AND t.created_at &lt; #{end}"
          + FILTER
          + " GROUP BY t.skill_id</script>")
  List<Tasks> tasks(UUID owner, Instant start, Instant end, UUID skill);

  @Select(
      "<script>SELECT t.skill_id,(s.ended_at AT TIME ZONE #{zone})::date AS day,sum(s.accumulated_millis)::bigint AS milliseconds FROM learning_sessions s JOIN learning_tasks t ON t.owner_id=s.owner_id AND t.id=s.task_id WHERE s.owner_id=#{owner} AND s.state='ENDED' AND s.ended_at>=#{start} AND s.ended_at &lt; #{end}"
          + FILTER
          + " GROUP BY t.skill_id,day ORDER BY day,t.skill_id</script>")
  List<Time> time(UUID owner, Instant start, Instant end, String zone, UUID skill);

  @Select(
      "<script>SELECT t.skill_id,(a.created_at AT TIME ZONE #{zone})::date AS day,count(*) AS total,count(*) FILTER(WHERE a.independent) AS independent,count(*) FILTER(WHERE a.independent AND r.verdict IN ('CORRECT','INCORRECT')) AS judged,count(*) FILTER(WHERE a.independent AND r.verdict='CORRECT') AS correct,count(*) FILTER(WHERE r.verdict IS NULL OR r.verdict='UNSURE') AS unsure FROM learning_attempts a JOIN learning_tasks t ON t.owner_id=a.owner_id AND t.id=a.task_id LEFT JOIN LATERAL (SELECT verdict FROM attempt_reviews r WHERE r.owner_id=a.owner_id AND r.attempt_id=a.id AND r.reviewer_type='RULE' AND r.created_at &lt; #{end} ORDER BY r.created_at DESC,r.id DESC LIMIT 1) r ON true WHERE a.owner_id=#{owner} AND a.created_at>=#{start} AND a.created_at &lt; #{end}"
          + FILTER
          + " GROUP BY t.skill_id,day ORDER BY day,t.skill_id</script>")
  List<Attempts> attempts(UUID owner, Instant start, Instant end, String zone, UUID skill);

  @Select(
      "<script>SELECT e.id AS enrollment_id,p.id AS path_id,p.title,count(*) FILTER(WHERE n.required AND pr.status='COMPLETED') AS completed_required,count(*) FILTER(WHERE n.required) AS total_required FROM path_enrollments e JOIN learning_paths p ON p.owner_id=e.owner_id AND p.id=e.path_id LEFT JOIN learning_path_nodes n ON n.owner_id=p.owner_id AND n.path_id=p.id LEFT JOIN learning_progress pr ON pr.owner_id=e.owner_id AND pr.enrollment_id=e.id AND pr.entry_id=n.id WHERE e.owner_id=#{owner} AND e.status='ACTIVE'<if test='skill != null'> AND p.skill_id=#{skill}</if> GROUP BY e.id,p.id,p.title</script>")
  List<Route> routes(UUID owner, UUID skill);

  String ASSESS =
      "FROM mastery_assessments a JOIN knowledge_nodes n ON n.owner_id=a.owner_id AND n.id=a.node_id WHERE a.owner_id=#{owner} AND a.assessed_at &lt; #{end}<if test='skill != null'> AND EXISTS(SELECT 1 FROM skill_nodes sn WHERE sn.owner_id=a.owner_id AND sn.node_id=a.node_id AND sn.skill_id=#{skill})</if>";

  @Select(
      "<script>SELECT id,node_id,title,level,confidence,assessor_type,reason,assessed_at FROM (SELECT DISTINCT ON(a.node_id,a.assessor_type) a.id,a.node_id,n.title,a.level,a.confidence,a.assessor_type,a.reason,a.assessed_at "
          + ASSESS
          + " ORDER BY a.node_id,a.assessor_type,a.assessed_at DESC,a.id DESC) latest ORDER BY assessed_at DESC,id LIMIT #{size} OFFSET #{offset}</script>")
  List<Assessment> assessments(UUID owner, UUID skill, Instant end, int size, int offset);

  @Select(
      "<script>SELECT count(*) FROM (SELECT DISTINCT a.node_id,a.assessor_type "
          + ASSESS
          + ") latest</script>")
  long assessmentCount(UUID owner, UUID skill, Instant end);

  @Select(
      "SELECT evidence_id FROM assessment_evidence WHERE owner_id=#{owner} AND assessment_id=#{id} ORDER BY evidence_id")
  List<UUID> evidence(UUID owner, UUID id);
}
