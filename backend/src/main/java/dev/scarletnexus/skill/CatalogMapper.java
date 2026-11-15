package dev.scarletnexus.skill;

import dev.scarletnexus.skill.CatalogModels.*;
import java.util.List;
import java.util.UUID;
import org.apache.ibatis.annotations.*;

@Mapper
public interface CatalogMapper {
  @Select("SELECT id FROM users WHERE id=#{owner} AND status='ACTIVE' FOR UPDATE")
  UUID lockOwner(UUID owner);

  @Select(
      "SELECT id,name,description,category,status FROM skills WHERE owner_id=#{owner} AND id=#{id}")
  Skill skill(UUID owner, UUID id);

  @Select(
      "SELECT id,name,description,category,status FROM skills WHERE owner_id=#{owner} AND (#{status}='ALL' OR status=#{status}) AND strpos(lower(name),lower(#{q}))>0 ORDER BY updated_at DESC,id LIMIT #{size} OFFSET #{offset}")
  List<Skill> skills(UUID owner, String q, String status, int size, int offset);

  @Select(
      "SELECT count(*) FROM skills WHERE owner_id=#{owner} AND (#{status}='ALL' OR status=#{status}) AND strpos(lower(name),lower(#{q}))>0")
  long skillCount(UUID owner, String q, String status);

  @Insert(
      "INSERT INTO skills(id,owner_id,name,description,category,status) VALUES(#{id},#{owner},#{name},#{description},#{category},#{status})")
  void insertSkill(
      UUID owner, UUID id, String name, String description, String category, String status);

  @Update(
      "UPDATE skills SET name=#{name},description=#{description},category=#{category},status=#{status},updated_at=now() WHERE owner_id=#{owner} AND id=#{id}")
  void updateSkill(
      UUID owner, UUID id, String name, String description, String category, String status);

  @Select(
      "SELECT id,title,node_type,description,status FROM knowledge_nodes WHERE owner_id=#{owner} AND id=#{id}")
  Node node(UUID owner, UUID id);

  @Select(
      "SELECT id,title,node_type,description,status FROM knowledge_nodes WHERE owner_id=#{owner} AND (#{status}='ALL' OR status=#{status}) AND strpos(lower(title),lower(#{q}))>0 ORDER BY updated_at DESC,id LIMIT #{size} OFFSET #{offset}")
  List<Node> nodes(UUID owner, String q, String status, int size, int offset);

  @Select(
      "SELECT count(*) FROM knowledge_nodes WHERE owner_id=#{owner} AND (#{status}='ALL' OR status=#{status}) AND strpos(lower(title),lower(#{q}))>0")
  long nodeCount(UUID owner, String q, String status);

  @Insert(
      "INSERT INTO knowledge_nodes(id,owner_id,title,node_type,description,status) VALUES(#{id},#{owner},#{title},#{kind},#{description},#{status})")
  void insertNode(
      UUID owner, UUID id, String title, String kind, String description, String status);

  @Update(
      "UPDATE knowledge_nodes SET title=#{title},node_type=#{kind},description=#{description},status=#{status},updated_at=now() WHERE owner_id=#{owner} AND id=#{id}")
  void updateNode(
      UUID owner, UUID id, String title, String kind, String description, String status);

  @Select(
      "SELECT node_id,parent_node_id,display_order FROM skill_nodes WHERE owner_id=#{owner} AND skill_id=#{skill}")
  List<Position> positions(UUID owner, UUID skill);

  @Insert(
      "INSERT INTO skill_nodes(id,owner_id,skill_id,node_id,parent_node_id,display_order) VALUES(#{id},#{owner},#{skill},#{node},#{parent},#{order})")
  void attach(UUID id, UUID owner, UUID skill, UUID node, UUID parent, int order);

  @Update(
      "UPDATE skill_nodes SET parent_node_id=#{parent},display_order=#{order},updated_at=now() WHERE owner_id=#{owner} AND skill_id=#{skill} AND node_id=#{node}")
  void move(UUID owner, UUID skill, UUID node, UUID parent, int order);

  @Delete(
      "DELETE FROM skill_nodes WHERE owner_id=#{owner} AND skill_id=#{skill} AND node_id=#{node}")
  void detach(UUID owner, UUID skill, UUID node);

  @Select(
      "SELECT n.id,n.title,n.node_type,n.description,n.status,p.parent_node_id,p.display_order FROM skill_nodes p JOIN knowledge_nodes n ON n.id=p.node_id AND n.owner_id=p.owner_id WHERE p.owner_id=#{owner} AND p.skill_id=#{skill} ORDER BY p.display_order,n.id")
  List<PlacedNode> placed(UUID owner, UUID skill);

  @Select(
      "SELECT id,source_node_id,target_node_id,relation_type FROM knowledge_relations WHERE owner_id=#{owner} ORDER BY created_at,id")
  List<Relation> relations(UUID owner);

  @Select(
      "SELECT id,source_node_id,target_node_id,relation_type FROM knowledge_relations WHERE owner_id=#{owner} AND id=#{id}")
  Relation relation(UUID owner, UUID id);

  @Insert(
      "INSERT INTO knowledge_relations(id,owner_id,source_node_id,target_node_id,relation_type) VALUES(#{id},#{owner},#{source},#{target},#{kind})")
  void insertRelation(UUID owner, UUID id, UUID source, UUID target, String kind);

  @Update(
      "UPDATE knowledge_relations SET source_node_id=#{source},target_node_id=#{target},relation_type=#{kind},updated_at=now() WHERE owner_id=#{owner} AND id=#{id}")
  void updateRelation(UUID owner, UUID id, UUID source, UUID target, String kind);

  @Delete("DELETE FROM knowledge_relations WHERE owner_id=#{owner} AND id=#{id}")
  void deleteRelation(UUID owner, UUID id);
}
