package dev.scarletnexus.knowledge;

import java.util.*;
import org.apache.ibatis.annotations.*;

@Mapper
public interface DraftMapper {
  @Select(
      "SELECT EXISTS(SELECT 1 FROM skill_nodes p WHERE p.owner_id=#{owner} AND p.node_id=#{node} AND p.skill_id<>#{skill} AND EXISTS(SELECT 1 FROM knowledge_graph_versions v WHERE v.owner_id=p.owner_id AND v.skill_id=p.skill_id))")
  boolean publishedElsewhere(UUID owner, UUID node, UUID skill);

  @Select(
      "SELECT EXISTS(SELECT 1 FROM skill_nodes p JOIN skill_nodes q ON q.owner_id=p.owner_id AND q.skill_id=p.skill_id WHERE p.owner_id=#{owner} AND p.node_id=#{source} AND q.node_id=#{target} AND p.skill_id<>#{skill} AND EXISTS(SELECT 1 FROM knowledge_graph_versions v WHERE v.owner_id=p.owner_id AND v.skill_id=p.skill_id))")
  boolean relationPublishedElsewhere(UUID owner, UUID source, UUID target, UUID skill);

  record Draft(
      UUID id, UUID skillId, String document, int revision, String baseHash, String status) {}

  record Version(UUID id, UUID skillId, UUID draftId, int versionNumber, String document) {}

  record PublishKey(UUID draftId, int revision, UUID versionId) {}

  @Select(
      "SELECT id,skill_id,draft_json::text,revision,base_hash,status FROM graph_drafts WHERE owner_id=#{owner} AND id=#{id}")
  Draft draft(UUID owner, UUID id);

  @Select(
      "SELECT id,skill_id,draft_json::text,revision,base_hash,status FROM graph_drafts WHERE owner_id=#{owner} AND skill_id=#{skill} ORDER BY created_at DESC")
  List<Draft> drafts(UUID owner, UUID skill);

  @Insert(
      "INSERT INTO graph_drafts(id,owner_id,skill_id,draft_json,base_hash) VALUES(#{id},#{owner},#{skill},CAST(#{document} AS JSONB),#{hash})")
  void create(UUID id, UUID owner, UUID skill, String document, String hash);

  @Update(
      "UPDATE graph_drafts SET draft_json=CAST(#{document} AS JSONB),revision=revision+1,updated_at=now() WHERE owner_id=#{owner} AND id=#{id} AND revision=#{revision} AND status='EDITING'")
  int save(UUID owner, UUID id, int revision, String document);

  @Update(
      "UPDATE graph_drafts SET status='PUBLISHED',updated_at=now() WHERE owner_id=#{owner} AND id=#{id}")
  void published(UUID owner, UUID id);

  @Select(
      "SELECT jsonb_build_object('skills',(SELECT jsonb_agg(to_jsonb(s) ORDER BY id) FROM skills s WHERE owner_id=#{owner}),'nodes',(SELECT jsonb_agg(to_jsonb(n) ORDER BY id) FROM knowledge_nodes n WHERE owner_id=#{owner}),'positions',(SELECT jsonb_agg(to_jsonb(p) ORDER BY id) FROM skill_nodes p WHERE owner_id=#{owner}),'relations',(SELECT jsonb_agg(to_jsonb(r) ORDER BY id) FROM knowledge_relations r WHERE owner_id=#{owner}))::text")
  String source(UUID owner);

  @Select("SELECT EXISTS(SELECT 1 FROM knowledge_nodes WHERE id=#{id})")
  boolean nodeExists(UUID id);

  @Select("SELECT EXISTS(SELECT 1 FROM knowledge_relations WHERE id=#{id})")
  boolean relationExists(UUID id);

  @Select("SELECT DISTINCT skill_id FROM skill_nodes WHERE owner_id=#{owner} ORDER BY skill_id")
  List<UUID> affectedSkills(UUID owner);

  @Delete("DELETE FROM skill_nodes WHERE owner_id=#{owner} AND skill_id=#{skill}")
  void clearPositions(UUID owner, UUID skill);

  @Select(
      "SELECT COALESCE(max(version_number),0)+1 FROM knowledge_graph_versions WHERE owner_id=#{owner} AND skill_id=#{skill}")
  int nextVersion(UUID owner, UUID skill);

  @Insert(
      "INSERT INTO knowledge_graph_versions(id,owner_id,skill_id,draft_id,version_number,snapshot_json) VALUES(#{id},#{owner},#{skill},#{draft},#{number},CAST(#{document} AS JSONB))")
  void version(UUID id, UUID owner, UUID skill, UUID draft, int number, String document);

  @Select(
      "SELECT id,skill_id,draft_id,version_number,snapshot_json::text FROM knowledge_graph_versions WHERE owner_id=#{owner} AND skill_id=#{skill} ORDER BY version_number DESC")
  List<Version> versions(UUID owner, UUID skill);

  @Select(
      "SELECT id,skill_id,draft_id,version_number,snapshot_json::text FROM knowledge_graph_versions WHERE owner_id=#{owner} AND id=#{id}")
  Version versionById(UUID owner, UUID id);

  @Select(
      "SELECT id,skill_id,draft_id,version_number,snapshot_json::text FROM knowledge_graph_versions WHERE owner_id=#{owner} AND draft_id=#{draft}")
  Version publishedVersion(UUID owner, UUID draft);

  @Select(
      "SELECT draft_id,revision,version_id FROM graph_publish_keys WHERE owner_id=#{owner} AND key=#{key}")
  PublishKey findKey(UUID owner, String key);

  @Insert(
      "INSERT INTO graph_publish_keys(owner_id,key,draft_id,revision,version_id) VALUES(#{owner},#{key},#{draft},#{revision},#{version})")
  void insertKey(UUID owner, String key, UUID draft, int revision, UUID version);
}
