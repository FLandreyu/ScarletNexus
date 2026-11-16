package dev.scarletnexus.transfer;

import static dev.scarletnexus.common.DomainFailure.require;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;
import dev.scarletnexus.common.*;
import dev.scarletnexus.identity.CurrentUser;
import dev.scarletnexus.knowledge.*;
import dev.scarletnexus.planner.PlanningSupport;
import dev.scarletnexus.skill.*;
import jakarta.validation.Validator;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class TransferService {
  static final List<String> SNAPSHOT_TABLES =
      List.of(
          "skills",
          "knowledge_nodes",
          "skill_nodes",
          "knowledge_relations",
          "graph_drafts",
          "knowledge_graph_versions",
          "learning_goals",
          "learning_paths",
          "learning_path_stages",
          "learning_path_nodes",
          "path_enrollments",
          "learning_progress",
          "learning_tasks",
          "learning_sessions",
          "learning_notes",
          "learning_resources",
          "node_resources",
          "questions",
          "question_exposures",
          "learning_attempts",
          "attachments",
          "attempt_attachments",
          "ai_conversations",
          "tutor_turns",
          "attempt_reviews",
          "learning_evidence",
          "mastery_assessments",
          "assessment_evidence",
          "review_schedules",
          "review_events",
          "review_cycle_tasks",
          "plan_drafts",
          "plan_draft_limits",
          "plan_draft_items",
          "daily_plans",
          "daily_plan_limits",
          "daily_plan_items",
          "daily_plan_days",
          "task_adjustments",
          "task_splits");
  private final TransferMapper db;
  private final CatalogMapper catalog;
  private final PlanningSupport support;
  private final GraphValidator graphs;
  private final Validator validator;
  private final AuditLog audit;
  private final ObjectMapper json;

  public TransferService(
      TransferMapper db,
      CatalogMapper catalog,
      PlanningSupport support,
      GraphValidator graphs,
      Validator validator,
      AuditLog audit,
      ObjectMapper json) {
    this.db = db;
    this.catalog = catalog;
    this.support = support;
    this.graphs = graphs;
    this.validator = validator;
    this.audit = audit;
    this.json =
        json.copy()
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
            .disable(MapperFeature.ALLOW_COERCION_OF_SCALARS)
            .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
    for (var shape :
        List.of(
            com.fasterxml.jackson.databind.cfg.CoercionInputShape.Integer,
            com.fasterxml.jackson.databind.cfg.CoercionInputShape.Float,
            com.fasterxml.jackson.databind.cfg.CoercionInputShape.Boolean)) {
      this.json
          .coercionConfigFor(com.fasterxml.jackson.databind.type.LogicalType.Textual)
          .setCoercion(shape, com.fasterxml.jackson.databind.cfg.CoercionAction.Fail);
    }
  }

  public record Preview(
      UUID id,
      String sourceHash,
      String state,
      int revision,
      int skills,
      int nodes,
      int positions,
      int relations,
      List<String> warnings) {}

  public record Result(
      UUID importId,
      Map<UUID, UUID> skillIds,
      Map<UUID, UUID> nodeIds,
      Map<UUID, UUID> relationIds) {}

  private String text(Object value) {
    try {
      return json.writeValueAsString(value);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException(e);
    }
  }

  private JsonNode tree(String value) {
    try {
      return json.readTree(value);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException(e);
    }
  }

  private CatalogDocument parse(String value) {
    require(
        value.getBytes(StandardCharsets.UTF_8).length <= 2097152,
        "IMPORT_TOO_LARGE",
        "目录JSON不能超过2MiB");
    try {
      JsonNode tree = json.readTree(value);
      checkUuidText(tree);
      var doc = json.treeToValue(tree, CatalogDocument.class);
      require(
          doc != null && validator.validate(doc).isEmpty(), "INVALID_CATALOG", "目录字段、类型或长度不符合v1合同");
      validate(doc);
      return doc;
    } catch (JsonProcessingException | IllegalArgumentException e) {
      throw new DomainFailure(
          org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY,
          "INVALID_CATALOG",
          "目录JSON格式、字段或UUID无效");
    }
  }

  private void checkUuidText(JsonNode node) {
    if (node.isObject()) {
      node.fields()
          .forEachRemaining(
              e -> {
                if (Set.of(
                            "id",
                            "skillId",
                            "nodeId",
                            "parentNodeId",
                            "sourceNodeId",
                            "targetNodeId")
                        .contains(e.getKey())
                    && !e.getValue().isNull())
                  require(
                      e.getValue().isTextual()
                          && e.getValue()
                              .asText()
                              .matches("[0-9a-fA-F]{8}(-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}"),
                      "INVALID_CATALOG_UUID",
                      "目录UUID无效");
                checkUuidText(e.getValue());
              });
    } else if (node.isArray()) node.forEach(this::checkUuidText);
  }

  private void validate(CatalogDocument doc) {
    Set<UUID> skills = new HashSet<>(), nodes = new HashSet<>();
    for (var sk : doc.skills()) require(skills.add(sk.id()), "DUPLICATE_CATALOG_ID", "技能ID重复");
    for (var node : doc.nodes()) require(nodes.add(node.id()), "DUPLICATE_CATALOG_ID", "节点ID重复");
    var defs = new HashMap<UUID, CatalogDocument.Node>();
    doc.nodes().forEach(n -> defs.put(n.id(), n));
    Set<String> seen = new HashSet<>();
    for (var p : doc.positions())
      require(
          skills.contains(p.skillId())
              && nodes.contains(p.nodeId())
              && seen.add(p.skillId() + ":" + p.nodeId()),
          "INVALID_CATALOG_POSITION",
          "位置引用缺失或重复");
    var global =
        new GraphDocument(
            1,
            doc.nodes().stream()
                .map(
                    n ->
                        new GraphDocument.Node(
                            n.id(), n.title(), n.nodeType(), n.description(), n.status(), null, 0))
                .toList(),
            doc.relations().stream()
                .map(
                    r ->
                        new GraphDocument.Relation(
                            r.id(), r.sourceNodeId(), r.targetNodeId(), r.relationType()))
                .toList());
    require(
        graphs.validate(global, List.of()).isEmpty(), "INVALID_CATALOG_GRAPH", "目录存在无效引用、重复关系或先修环");
    for (var skill : doc.skills()) {
      var positions =
          doc.positions().stream()
              .filter(p -> p.skillId().equals(skill.id()))
              .map(
                  p -> {
                    var n = defs.get(p.nodeId());
                    return new GraphDocument.Node(
                        n.id(),
                        n.title(),
                        n.nodeType(),
                        n.description(),
                        n.status(),
                        p.parentNodeId(),
                        p.displayOrder());
                  })
              .toList();
      require(
          graphs.validate(new GraphDocument(1, positions, List.of()), List.of()).isEmpty(),
          "INVALID_CATALOG_TREE",
          "技能分类存在父节点缺失或环");
    }
  }

  public CatalogDocument catalog(UUID skill) {
    support.lock();
    var owner = CurrentUser.id();
    var skills =
        skill == null
            ? catalog.skills(owner, "", "ALL", 101, 0)
            : List.of(support.found(catalog.skill(owner, skill)));
    require(
        skills.size() <= 100 && !skills.isEmpty(),
        "CATALOG_EXPORT_SCOPE",
        "请选择最多100个技能的范围；无技能时请先创建");
    var nodes = new TreeMap<UUID, CatalogDocument.Node>();
    var positions = new ArrayList<CatalogDocument.Position>();
    for (var s : skills)
      for (var p : catalog.positions(owner, s.id())) {
        var n = support.found(catalog.node(owner, p.nodeId()));
        nodes.put(
            n.id(),
            new CatalogDocument.Node(n.id(), n.title(), n.nodeType(), n.description(), n.status()));
        positions.add(
            new CatalogDocument.Position(s.id(), p.nodeId(), p.parentNodeId(), p.displayOrder()));
      }
    var relations =
        catalog.relations(owner).stream()
            .filter(r -> nodes.containsKey(r.sourceNodeId()) && nodes.containsKey(r.targetNodeId()))
            .map(
                r ->
                    new CatalogDocument.Relation(
                        r.id(), r.sourceNodeId(), r.targetNodeId(), r.relationType()))
            .toList();
    var doc =
        new CatalogDocument(
            "scarlet-nexus-catalog/v1",
            skills.stream()
                .map(
                    s ->
                        new CatalogDocument.Skill(
                            s.id(), s.name(), s.description(), s.category(), s.status()))
                .toList(),
            List.copyOf(nodes.values()),
            positions,
            relations);
    require(validator.validate(doc).isEmpty(), "CATALOG_EXPORT_SCOPE", "目录过大，请选择单个技能");
    return doc;
  }

  public Preview preview(String document) {
    support.lock();
    var doc = parse(document);
    String normalized = text(doc);
    UUID id = UUID.randomUUID();
    db.create(CurrentUser.id(), id, normalized, support.hash(doc));
    var result = preview(id);
    audit.record("catalog_import", id, "PREVIEW", null, result);
    return result;
  }

  public Preview preview(UUID id) {
    var row = support.found(db.get(CurrentUser.id(), id));
    var doc = parse(row.documentJson());
    return new Preview(
        id,
        row.sourceHash(),
        row.state(),
        row.revision(),
        doc.skills().size(),
        doc.nodes().size(),
        doc.positions().size(),
        doc.relations().size(),
        List.of("只导入知识目录；不含学习进度、路线、证据或附件。", "确认后新增技能和节点UUID，保留共享引用；不覆盖现有记录，也不自动发布图谱版本。"));
  }

  public Result confirm(UUID id, int revision, String hash, boolean confirmed, String key) {
    support.lock();
    support.key(key);
    require(confirmed, "EXPLICIT_CONFIRMATION_REQUIRED", "请明确确认仅新增知识目录");
    var row = support.found(db.get(CurrentUser.id(), id));
    var replay = db.replay(CurrentUser.id(), key);
    if (replay != null) {
      support.conflict(replay.id().equals(id) && replay.sourceHash().equals(hash) && revision == 0);
      return result(replay.resultJson());
    }
    support.conflict(
        row.state().equals("DRAFT") && row.revision() == revision && row.sourceHash().equals(hash));
    var doc = parse(row.documentJson());
    Map<UUID, UUID> skills = new LinkedHashMap<>(),
        nodes = new LinkedHashMap<>(),
        relations = new LinkedHashMap<>();
    UUID owner = CurrentUser.id();
    for (var s : doc.skills()) {
      UUID next = UUID.randomUUID();
      skills.put(s.id(), next);
      catalog.insertSkill(owner, next, s.name().strip(), s.description(), s.category(), s.status());
    }
    for (var n : doc.nodes()) {
      UUID next = UUID.randomUUID();
      nodes.put(n.id(), next);
      catalog.insertNode(owner, next, n.title().strip(), n.nodeType(), n.description(), n.status());
    }
    var pending = new ArrayList<>(doc.positions());
    Set<String> attached = new HashSet<>();
    while (!pending.isEmpty()) {
      int before = pending.size();
      var iterator = pending.iterator();
      while (iterator.hasNext()) {
        var p = iterator.next();
        if (p.parentNodeId() != null && !attached.contains(p.skillId() + ":" + p.parentNodeId()))
          continue;
        catalog.attach(
            UUID.randomUUID(),
            owner,
            skills.get(p.skillId()),
            nodes.get(p.nodeId()),
            nodes.get(p.parentNodeId()),
            p.displayOrder());
        attached.add(p.skillId() + ":" + p.nodeId());
        iterator.remove();
      }
      require(pending.size() < before, "INVALID_CATALOG_TREE", "分类引用无法按父节点顺序建立");
    }
    for (var r : doc.relations()) {
      UUID next = UUID.randomUUID();
      relations.put(r.id(), next);
      catalog.insertRelation(
          owner, next, nodes.get(r.sourceNodeId()), nodes.get(r.targetNodeId()), r.relationType());
    }
    var result = new Result(id, skills, nodes, relations);
    db.confirm(owner, id, key, text(result));
    audit.record("catalog_import", id, "CONFIRM", preview(id), result);
    return result;
  }

  private Result result(String value) {
    try {
      return json.readValue(value, Result.class);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException(e);
    }
  }

  public JsonNode snapshot() {
    support.lock();
    long count = 0, bytes = 0;
    var counts = new LinkedHashMap<String, Long>();
    for (String table : SNAPSHOT_TABLES) {
      var size = db.size(CurrentUser.id(), table);
      long n = ((Number) size.get("rows")).longValue();
      count += n;
      bytes += ((Number) size.get("bytes")).longValue();
      counts.put(table, n);
    }
    require(
        count <= 25000 && bytes <= 8388608, "SNAPSHOT_TOO_LARGE", "学习快照超过25000行或8MiB，请使用运维完整备份");
    var result =
        json.createObjectNode()
            .put("format", "scarlet-nexus-learning-snapshot/v1")
            .put("exportedAt", Instant.now().toString())
            .put("schemaVersion", "11");
    result.put("scope", "当前用户业务快照；不是可直接恢复的数据库/附件备份，不含身份凭据、密钥、审计事件和文件二进制。");
    var tables = result.putObject("tables");
    for (String table : SNAPSHOT_TABLES) {
      var array = tables.putArray(table);
      for (String row : db.rows(CurrentUser.id(), table)) array.add(tree(row));
    }
    result.set("counts", json.valueToTree(counts));
    return result;
  }
}
