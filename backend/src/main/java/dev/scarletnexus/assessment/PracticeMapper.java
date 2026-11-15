package dev.scarletnexus.assessment;

import dev.scarletnexus.assessment.PracticeModels.*;
import java.util.*;
import org.apache.ibatis.annotations.*;

@Mapper
public interface PracticeMapper {
  @Select(
      "SELECT id,node_id,title,prompt,question_type,status,answer_key,hint_text FROM questions WHERE owner_id=#{owner} AND id=#{id}")
  Secret secret(UUID owner, UUID id);

  @Select(
      "SELECT id,node_id,title,prompt,question_type,status FROM questions WHERE owner_id=#{owner} AND node_id=#{node} ORDER BY created_at DESC,id LIMIT #{size} OFFSET #{offset}")
  List<Question> questions(UUID owner, UUID node, int size, int offset);

  @Select("SELECT count(*) FROM questions WHERE owner_id=#{owner} AND node_id=#{node}")
  long questionCount(UUID owner, UUID node);

  @Insert(
      "INSERT INTO questions(id,owner_id,node_id,title,prompt,question_type,answer_key,hint_text) VALUES(#{id},#{owner},#{node},#{title},#{prompt},#{type},#{answer},#{hint})")
  void questionInsert(
      UUID owner,
      UUID id,
      UUID node,
      String title,
      String prompt,
      String type,
      String answer,
      String hint);

  @Update("UPDATE questions SET status=#{status} WHERE owner_id=#{owner} AND id=#{id}")
  void questionState(UUID owner, UUID id, String status);

  @Select(
      "SELECT COALESCE((SELECT hint_level FROM question_exposures WHERE owner_id=#{owner} AND question_id=#{question}),0)")
  int exposure(UUID owner, UUID question);

  @Insert(
      "INSERT INTO question_exposures(owner_id,question_id,hint_level) VALUES(#{owner},#{question},#{level}) ON CONFLICT(owner_id,question_id) DO UPDATE SET hint_level=greatest(question_exposures.hint_level,EXCLUDED.hint_level),updated_at=now()")
  void expose(UUID owner, UUID question, int level);

  @Select(
      "SELECT id,input_hash FROM learning_attempts WHERE owner_id=#{owner} AND request_key=#{key}")
  AttemptKey attemptKey(UUID owner, String key);

  @Insert(
      "INSERT INTO learning_attempts(id,owner_id,task_id,node_id,question_id,submitted_content,independent,hint_level_at_submission,request_key,input_hash) VALUES(#{id},#{owner},#{task},#{node},#{question},#{content},#{independent},#{level},#{key},#{hash})")
  void attemptInsert(
      UUID owner,
      UUID id,
      UUID task,
      UUID node,
      UUID question,
      String content,
      boolean independent,
      int level,
      String key,
      String hash);

  @Select(
      "SELECT id,task_id,node_id,question_id,submitted_content,independent,hint_level_at_submission,created_at FROM learning_attempts WHERE owner_id=#{owner} AND id=#{id}")
  Attempt attempt(UUID owner, UUID id);

  @Select(
      "SELECT id,task_id,node_id,question_id,submitted_content,independent,hint_level_at_submission,created_at FROM learning_attempts WHERE owner_id=#{owner} AND task_id=#{task} ORDER BY created_at DESC,id LIMIT #{size} OFFSET #{offset}")
  List<Attempt> attempts(UUID owner, UUID task, int size, int offset);

  @Select("SELECT count(*) FROM learning_attempts WHERE owner_id=#{owner} AND task_id=#{task}")
  long attemptCount(UUID owner, UUID task);

  @Select(
      "SELECT id,input_hash FROM attempt_reviews WHERE owner_id=#{owner} AND request_key=#{key}")
  ReviewKey reviewKey(UUID owner, String key);

  @Insert(
      "INSERT INTO attempt_reviews(id,owner_id,attempt_id,tutor_turn_id,verdict,reviewer_type,feedback,request_key,input_hash) VALUES(#{id},#{owner},#{attempt},#{turn},#{verdict},#{type},#{feedback},#{key},#{hash})")
  void reviewInsert(
      UUID owner,
      UUID id,
      UUID attempt,
      UUID turn,
      String verdict,
      String type,
      String feedback,
      String key,
      String hash);

  @Select(
      "SELECT id,attempt_id,tutor_turn_id,verdict,reviewer_type,feedback,created_at FROM attempt_reviews WHERE owner_id=#{owner} AND id=#{id}")
  Review review(UUID owner, UUID id);

  @Select(
      "SELECT id,attempt_id,tutor_turn_id,verdict,reviewer_type,feedback,created_at FROM attempt_reviews WHERE owner_id=#{owner} AND attempt_id=#{attempt} ORDER BY created_at DESC,id LIMIT #{size} OFFSET #{offset}")
  List<Review> reviews(UUID owner, UUID attempt, int size, int offset);

  @Select("SELECT count(*) FROM attempt_reviews WHERE owner_id=#{owner} AND attempt_id=#{attempt}")
  long reviewCount(UUID owner, UUID attempt);

  @Select(
      "SELECT EXISTS(SELECT 1 FROM tutor_turns t JOIN ai_conversations c ON c.id=t.conversation_id AND c.owner_id=t.owner_id WHERE t.owner_id=#{owner} AND t.id=#{turn} AND t.attempt_id=#{attempt} AND t.state='SUCCEEDED')")
  boolean reviewedTurn(UUID owner, UUID turn, UUID attempt);

  @Select(
      "SELECT id,node_id,attempt_id,review_id,evidence_type,verification,outcome,independent,observed_at FROM learning_evidence WHERE owner_id=#{owner} AND review_id=#{review}")
  Evidence reviewEvidence(UUID owner, UUID review);

  @Select(
      "SELECT id,node_id,attempt_id,review_id,evidence_type,verification,outcome,independent,observed_at FROM learning_evidence WHERE owner_id=#{owner} AND id=#{id}")
  Evidence evidence(UUID owner, UUID id);

  @Select(
      "SELECT id,node_id,attempt_id,review_id,evidence_type,verification,outcome,independent,observed_at FROM learning_evidence WHERE owner_id=#{owner} AND node_id=#{node} ORDER BY observed_at DESC,id LIMIT #{size} OFFSET #{offset}")
  List<Evidence> evidenceList(UUID owner, UUID node, int size, int offset);

  @Select("SELECT count(*) FROM learning_evidence WHERE owner_id=#{owner} AND node_id=#{node}")
  long evidenceCount(UUID owner, UUID node);

  @Insert(
      "INSERT INTO learning_evidence(id,owner_id,node_id,attempt_id,review_id,evidence_type,verification,outcome,independent) VALUES(#{id},#{owner},#{node},#{attempt},#{review},#{type},#{verification},#{outcome},#{independent})")
  void evidenceInsert(
      UUID owner,
      UUID id,
      UUID node,
      UUID attempt,
      UUID review,
      String type,
      String verification,
      String outcome,
      boolean independent);

  @Insert(
      "INSERT INTO mastery_assessments(id,owner_id,node_id,level,confidence,assessor_type,reason) VALUES(#{id},#{owner},#{node},#{level},#{confidence},#{type},#{reason})")
  void assessmentInsert(
      UUID owner, UUID id, UUID node, int level, String confidence, String type, String reason);

  @Insert(
      "INSERT INTO assessment_evidence(owner_id,assessment_id,node_id,evidence_id) VALUES(#{owner},#{assessment},#{node},#{evidence})")
  void assessmentLink(UUID owner, UUID assessment, UUID node, UUID evidence);

  @Select(
      "SELECT id,node_id,level,confidence,assessor_type,reason,assessed_at FROM mastery_assessments WHERE owner_id=#{owner} AND node_id=#{node} ORDER BY assessed_at DESC,id LIMIT #{size} OFFSET #{offset}")
  List<Assessment> assessments(UUID owner, UUID node, int size, int offset);

  @Select("SELECT count(*) FROM mastery_assessments WHERE owner_id=#{owner} AND node_id=#{node}")
  long assessmentCount(UUID owner, UUID node);

  @Select(
      "SELECT evidence_id FROM assessment_evidence WHERE owner_id=#{owner} AND assessment_id=#{assessment} ORDER BY evidence_id")
  List<UUID> assessmentEvidence(UUID owner, UUID assessment);

  @Select(
      "SELECT id,task_id,size_bytes,width,height,status,created_at FROM attachments WHERE owner_id=#{owner} AND task_id=#{task} AND status<>'QUARANTINED' ORDER BY created_at DESC,id LIMIT #{size} OFFSET #{offset}")
  List<Attachment> attachments(UUID owner, UUID task, int size, int offset);

  @Select(
      "SELECT count(*) FROM attachments WHERE owner_id=#{owner} AND task_id=#{task} AND status<>'QUARANTINED'")
  long attachmentCount(UUID owner, UUID task);

  @Select(
      "SELECT id,task_id,storage_key,checksum,size_bytes,width,height,status FROM attachments WHERE owner_id=#{owner} AND id=#{id}")
  StoredAttachment storedAttachment(UUID owner, UUID id);

  @Insert(
      "INSERT INTO attachments(id,owner_id,task_id,storage_key,checksum,size_bytes,width,height,status) VALUES(#{id},#{owner},#{task},#{storage},#{hash},#{size},#{width},#{height},'QUARANTINED')")
  void attachmentInsert(
      UUID owner,
      UUID id,
      UUID task,
      String storage,
      String hash,
      long size,
      int width,
      int height);

  @Update("UPDATE attachments SET status=#{status} WHERE owner_id=#{owner} AND id=#{id}")
  void attachmentState(UUID owner, UUID id, String status);

  @Insert(
      "INSERT INTO attempt_attachments(owner_id,attempt_id,attachment_id) VALUES(#{owner},#{attempt},#{attachment})")
  void attemptAttachment(UUID owner, UUID attempt, UUID attachment);

  @Select(
      "SELECT a.id,a.task_id,a.size_bytes,a.width,a.height,a.status,a.created_at FROM attachments a JOIN attempt_attachments r ON a.owner_id=r.owner_id AND a.id=r.attachment_id WHERE r.owner_id=#{owner} AND r.attempt_id=#{attempt} ORDER BY a.created_at,a.id")
  List<Attachment> attemptAttachments(UUID owner, UUID attempt);
}
