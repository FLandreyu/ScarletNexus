package dev.scarletnexus.skill;

import static dev.scarletnexus.common.DomainFailure.require;

import dev.scarletnexus.common.AuditLog;
import dev.scarletnexus.common.DomainFailure;
import dev.scarletnexus.identity.CurrentUser;
import dev.scarletnexus.skill.CatalogModels.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class CatalogService {
  private final CatalogMapper db;
  private final AuditLog audit;

  public CatalogService(CatalogMapper db, AuditLog audit) {
    this.db = db;
    this.audit = audit;
  }

  private UUID owner() {
    return CurrentUser.id();
  }

  private void lock() {
    if (db.lockOwner(owner()) == null) throw DomainFailure.missing();
  }

  public Skill skill(UUID id) {
    var row = db.skill(owner(), id);
    if (row == null) throw DomainFailure.missing();
    return row;
  }

  public Node node(UUID id) {
    var row = db.node(owner(), id);
    if (row == null) throw DomainFailure.missing();
    return row;
  }

  public Relation relation(UUID id) {
    var row = db.relation(owner(), id);
    if (row == null) throw DomainFailure.missing();
    return row;
  }

  private void page(String q, String status, int page, int size) {
    require(
        q.length() <= 160
            && page >= 0
            && page <= 100000
            && size >= 1
            && size <= 100
            && Set.of("ALL", "ACTIVE", "ARCHIVED").contains(status),
        "INVALID_QUERY",
        "查询参数无效");
  }

  public Page<Skill> skills(String q, String status, int page, int size) {
    page(q, status, page, size);
    return new Page<>(
        db.skills(owner(), q, status, size, page * size),
        page,
        size,
        db.skillCount(owner(), q, status));
  }

  public Page<Node> nodes(String q, String status, int page, int size) {
    page(q, status, page, size);
    return new Page<>(
        db.nodes(owner(), q, status, size, page * size),
        page,
        size,
        db.nodeCount(owner(), q, status));
  }

  public Skill saveSkill(UUID id, CatalogController.SkillWrite value) {
    lock();
    Skill before = id == null ? null : skill(id);
    UUID key = id == null ? UUID.randomUUID() : id;
    if (before == null)
      db.insertSkill(
          owner(),
          key,
          value.name().strip(),
          value.description(),
          value.category().strip(),
          value.status());
    else
      db.updateSkill(
          owner(),
          key,
          value.name().strip(),
          value.description(),
          value.category().strip(),
          value.status());
    Skill after = skill(key);
    audit.record("SKILL", key, before == null ? "CREATED" : "UPDATED", before, after);
    return after;
  }

  public Node saveNode(UUID id, CatalogController.NodeWrite value) {
    lock();
    Node before = id == null ? null : node(id);
    UUID key = id == null ? UUID.randomUUID() : id;
    if (before == null)
      db.insertNode(
          owner(),
          key,
          value.title().strip(),
          value.nodeType(),
          value.description(),
          value.status());
    else
      db.updateNode(
          owner(),
          key,
          value.title().strip(),
          value.nodeType(),
          value.description(),
          value.status());
    Node after = node(key);
    audit.record("KNOWLEDGE_NODE", key, before == null ? "CREATED" : "UPDATED", before, after);
    return after;
  }

  public Graph graph(UUID skillId) {
    Skill skill = skill(skillId);
    List<PlacedNode> nodes = db.placed(owner(), skillId);
    Set<UUID> ids = new HashSet<>();
    nodes.forEach(node -> ids.add(node.id()));
    return new Graph(
        skill,
        nodes,
        db.relations(owner()).stream()
            .filter(r -> ids.contains(r.sourceNodeId()) && ids.contains(r.targetNodeId()))
            .toList());
  }

  public Position place(
      UUID skillId, UUID nodeId, CatalogController.PositionWrite value, boolean create) {
    lock();
    skill(skillId);
    Node node = node(nodeId);
    require("ACTIVE".equals(node.status()), "NODE_ARCHIVED", "请先恢复已归档节点");
    List<Position> all = db.positions(owner(), skillId);
    Position before = all.stream().filter(p -> p.nodeId().equals(nodeId)).findFirst().orElse(null);
    require(create ? before == null : before != null, "POSITION_CONFLICT", "节点引用已存在或不存在，请刷新后重试");
    Map<UUID, UUID> parents = new HashMap<>();
    all.forEach(p -> parents.put(p.nodeId(), p.parentNodeId()));
    UUID parent = value.parentNodeId();
    if (parent != null) {
      node(parent);
      require(parents.containsKey(parent), "INVALID_PARENT", "父节点必须已属于当前技能");
    }
    Set<UUID> visited = new HashSet<>();
    UUID cursor = parent;
    while (cursor != null) {
      require(!cursor.equals(nodeId) && visited.add(cursor), "CATEGORY_CYCLE", "分类关系不能形成环");
      cursor = parents.get(cursor);
    }
    if (create)
      db.attach(UUID.randomUUID(), owner(), skillId, nodeId, parent, value.displayOrder());
    else db.move(owner(), skillId, nodeId, parent, value.displayOrder());
    Position after = new Position(nodeId, parent, value.displayOrder());
    audit.record("SKILL", skillId, create ? "NODE_ATTACHED" : "NODE_MOVED", before, after);
    return after;
  }

  public void detach(UUID skillId, UUID nodeId) {
    lock();
    skill(skillId);
    node(nodeId);
    var positions = db.positions(owner(), skillId);
    Position before =
        positions.stream()
            .filter(p -> p.nodeId().equals(nodeId))
            .findFirst()
            .orElseThrow(DomainFailure::missing);
    require(
        positions.stream().noneMatch(p -> nodeId.equals(p.parentNodeId())),
        "HAS_CHILDREN",
        "请先移动子节点，再移除此引用");
    db.detach(owner(), skillId, nodeId);
    audit.record("SKILL", skillId, "NODE_DETACHED", before, null);
  }

  public List<Relation> relations() {
    return db.relations(owner());
  }

  public Relation saveRelation(UUID id, CatalogController.RelationWrite value) {
    lock();
    var source = node(value.sourceNodeId());
    var target = node(value.targetNodeId());
    require(
        "ACTIVE".equals(source.status()) && "ACTIVE".equals(target.status()),
        "NODE_ARCHIVED",
        "已归档节点不能建立新关系");
    require(!source.id().equals(target.id()), "SELF_REFERENCE", "关系不能指向自身");
    Relation before = id == null ? null : relation(id);
    UUID key = id == null ? UUID.randomUUID() : id;
    if ("PREREQUISITE_OF".equals(value.relationType())) {
      Map<UUID, List<UUID>> edges = new HashMap<>();
      for (var edge : db.relations(owner()))
        if (!edge.id().equals(key) && "PREREQUISITE_OF".equals(edge.relationType()))
          edges
              .computeIfAbsent(edge.sourceNodeId(), ignored -> new ArrayList<>())
              .add(edge.targetNodeId());
      var queue = new ArrayDeque<UUID>();
      var seen = new HashSet<UUID>();
      queue.add(target.id());
      while (!queue.isEmpty()) {
        UUID next = queue.remove();
        require(!next.equals(source.id()), "DEPENDENCY_CYCLE", "先修依赖不能形成环");
        if (seen.add(next)) queue.addAll(edges.getOrDefault(next, List.of()));
      }
    }
    if (before == null)
      db.insertRelation(owner(), key, source.id(), target.id(), value.relationType());
    else db.updateRelation(owner(), key, source.id(), target.id(), value.relationType());
    Relation after = relation(key);
    audit.record("KNOWLEDGE_RELATION", key, before == null ? "CREATED" : "UPDATED", before, after);
    return after;
  }

  public void deleteRelation(UUID id) {
    lock();
    Relation before = relation(id);
    db.deleteRelation(owner(), id);
    audit.record("KNOWLEDGE_RELATION", id, "REMOVED", before, null);
  }
}
