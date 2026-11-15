package dev.scarletnexus.knowledge;

import com.fasterxml.jackson.databind.*;
import dev.scarletnexus.common.*;
import dev.scarletnexus.identity.CurrentUser;
import dev.scarletnexus.knowledge.DraftMapper.*;
import dev.scarletnexus.skill.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class DraftService {
  private final DraftMapper db;
  private final CatalogMapper catalog;
  private final CatalogService skills;
  private final ObjectMapper json;
  private final GraphValidator validator;
  private final AuditLog audit;

  public DraftService(
      DraftMapper db,
      CatalogMapper catalog,
      CatalogService skills,
      ObjectMapper json,
      GraphValidator validator,
      AuditLog audit) {
    this.db = db;
    this.catalog = catalog;
    this.skills = skills;
    this.json = json;
    this.validator = validator;
    this.audit = audit;
  }

  private UUID owner() {
    return CurrentUser.id();
  }

  private void lock() {
    if (catalog.lockOwner(owner()) == null) throw DomainFailure.missing();
  }

  private String hash() {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256")
                  .digest(db.source(owner()).getBytes(StandardCharsets.UTF_8)));
    } catch (Exception e) {
      throw new IllegalStateException("Cannot fingerprint graph");
    }
  }

  private void conflict(boolean ok) {
    if (!ok)
      throw new DomainFailure(HttpStatus.CONFLICT, "DRAFT_CONFLICT", "草案版本或正式数据已变化，请刷新或创建新草案");
  }

  public Draft get(UUID id) {
    Draft d = db.draft(owner(), id);
    if (d == null) throw DomainFailure.missing();
    return d;
  }

  public List<Draft> list(UUID skill) {
    skills.skill(skill);
    return db.drafts(owner(), skill);
  }

  public List<Version> versions(UUID skill) {
    skills.skill(skill);
    return db.versions(owner(), skill);
  }

  public Version version(UUID id) {
    var v = db.versionById(owner(), id);
    if (v == null) throw DomainFailure.missing();
    return v;
  }

  public Draft create(UUID skill) {
    lock();
    var graph = skills.graph(skill);
    var doc =
        new GraphDocument(
            1,
            graph.nodes().stream()
                .map(
                    n ->
                        new GraphDocument.Node(
                            n.id(),
                            n.title(),
                            n.nodeType(),
                            n.description(),
                            n.status(),
                            n.parentNodeId(),
                            n.displayOrder()))
                .toList(),
            graph.relations().stream()
                .map(
                    r ->
                        new GraphDocument.Relation(
                            r.id(), r.sourceNodeId(), r.targetNodeId(), r.relationType()))
                .toList());
    UUID id = UUID.randomUUID();
    db.create(id, owner(), skill, json.valueToTree(doc).toString(), hash());
    audit.record("GRAPH_DRAFT", id, "CREATED", null, Map.of("skillId", skill));
    return get(id);
  }

  public Draft save(UUID id, int revision, JsonNode document) {
    lock();
    Draft before = get(id);
    DomainFailure.require(
        document != null && document.isObject() && document.toString().length() <= 1_000_000,
        "INVALID_DOCUMENT",
        "草案必须是大小受限的JSON对象");
    conflict(db.save(owner(), id, revision, document.toString()) == 1);
    audit.record(
        "GRAPH_DRAFT",
        id,
        "SAVED",
        Map.of("revision", before.revision(), "document", before.document()),
        Map.of("revision", revision + 1, "document", document));
    return get(id);
  }

  public Draft transform(
      UUID id, int revision, String kind, UUID source, UUID target, List<String> titles) {
    lock();
    var before = get(id);
    conflict(before.revision() == revision && "EDITING".equals(before.status()));
    GraphDocument document;
    try {
      document = validator.parse(json.readTree(before.document()));
    } catch (Exception e) {
      throw new DomainFailure(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_GRAPH", "请先修复草案结构再进行合并拆分");
    }
    var changed = GraphTransformer.transform(document, kind, source, target, titles);
    GraphDocument normalized;
    try {
      normalized = validator.parse(json.valueToTree(changed));
    } catch (IllegalArgumentException e) {
      throw new DomainFailure(
          HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_TRANSFORM", "转换后草案超出结构或大小限制");
    }
    DomainFailure.require(
        validator.validate(normalized, List.of()).isEmpty(),
        "INVALID_TRANSFORM",
        "转换产生无效分类或依赖，请先调整结构");
    var result = save(id, revision, json.valueToTree(normalized));
    audit.record(
        "GRAPH_DRAFT",
        id,
        kind,
        null,
        Map.of("source", source, "mapping", normalized.mappings().getLast()));
    return result;
  }

  public record Preview(
      boolean valid,
      List<String> issues,
      List<UUID> potentiallyAffectedSkills,
      boolean sourceChanged,
      int nodeCount,
      int relationCount,
      List<String> warnings) {}

  public Preview preview(UUID id) {
    return preview(id, false);
  }

  public Preview preview(UUID id, boolean includeAffectedSkills) {
    lock();
    var draft = get(id);
    List<String> errors = new ArrayList<>();
    GraphDocument doc;
    try {
      doc = validator.parse(json.readTree(draft.document()));
    } catch (Exception e) {
      return new Preview(
          false,
          List.of("草案结构不符合版本合同"),
          List.of(),
          !draft.baseHash().equals(hash()),
          0,
          0,
          List.of());
    }
    Set<UUID> oldIds = new HashSet<>();
    catalog.positions(owner(), draft.skillId()).forEach(p -> oldIds.add(p.nodeId()));
    List<GraphDocument.Relation> external = new ArrayList<>();
    Set<UUID> internalRelations = new HashSet<>();
    for (var r : catalog.relations(owner()))
      if (oldIds.contains(r.sourceNodeId()) && oldIds.contains(r.targetNodeId()))
        internalRelations.add(r.id());
      else
        external.add(
            new GraphDocument.Relation(
                r.id(), r.sourceNodeId(), r.targetNodeId(), r.relationType()));
    errors.addAll(validator.validate(doc, external));
    for (var n : doc.nodes()) {
      var old = catalog.node(owner(), n.id());
      if (old != null
          && !includeAffectedSkills
          && db.publishedElsewhere(owner(), n.id(), draft.skillId())
          && (!old.title().equals(n.title().strip())
              || !old.nodeType().equals(n.nodeType())
              || !old.description().equals(n.description())
              || !old.status().equals(n.status()))) errors.add("共享节点已被其他已发布技能引用，当前单技能发布不支持修改其定义");
    }
    Map<UUID, GraphDocument.Relation> replacements = new HashMap<>();
    doc.relations().forEach(r -> replacements.put(r.id(), r));
    Map<UUID, GraphDocument.Relation> previous = new HashMap<>();
    for (var r : catalog.relations(owner()))
      if (internalRelations.contains(r.id()))
        previous.put(
            r.id(),
            new GraphDocument.Relation(
                r.id(), r.sourceNodeId(), r.targetNodeId(), r.relationType()));
    for (var r : previous.values())
      if (!r.equals(replacements.get(r.id()))
          && !includeAffectedSkills
          && db.relationPublishedElsewhere(
              owner(), r.sourceNodeId(), r.targetNodeId(), draft.skillId()))
        errors.add("关系修改会影响其他已发布技能，需要联合发布支持");
    for (var r : doc.relations())
      if (!r.equals(previous.get(r.id()))
          && !includeAffectedSkills
          && db.relationPublishedElsewhere(
              owner(), r.sourceNodeId(), r.targetNodeId(), draft.skillId()))
        errors.add("关系修改会影响其他已发布技能，需要联合发布支持");
    for (var n : doc.nodes())
      if (db.nodeExists(n.id()) && catalog.node(owner(), n.id()) == null) errors.add("节点ID不可用");
    for (var mapping : doc.mappings()) {
      var ids = new HashSet<>(mapping.targets());
      mapping.originals().forEach(n -> ids.add(n.id()));
      for (UUID node : ids)
        if (db.nodeExists(node) && catalog.node(owner(), node) == null) errors.add("来源映射节点ID不可用");
    }
    for (var r : doc.relations())
      if (db.relationExists(r.id()) && !internalRelations.contains(r.id()))
        errors.add("关系ID不可用于当前技能草案");
    boolean changed = !draft.baseHash().equals(hash());
    if (changed) errors.add("正式数据已变化，需要新草案");
    return new Preview(
        errors.isEmpty(),
        errors.stream().distinct().toList(),
        db.affectedSkills(owner()),
        changed,
        doc.nodes().size(),
        doc.relations().size(),
        validator.duplicateCandidates(doc));
  }

  public Version publish(UUID id, int revision, boolean confirmed, String key) {
    return publish(id, revision, confirmed, key, false);
  }

  public Version publish(
      UUID id, int revision, boolean confirmed, String key, boolean includeAffectedSkills) {
    lock();
    var draft = get(id);
    DomainFailure.require(
        confirmed && key != null && key.matches("[A-Za-z0-9._-]{1,128}"),
        "CONFIRMATION_REQUIRED",
        "需要确认和有效幂等键");
    var prior = db.findKey(owner(), key);
    if (prior != null) {
      conflict(prior.draftId().equals(id) && prior.revision() == revision);
      return version(prior.versionId());
    }
    conflict(draft.revision() == revision);
    var published = db.publishedVersion(owner(), id);
    if (published != null) {
      db.insertKey(owner(), key, id, revision, published.id());
      return published;
    }
    var preview = preview(id, includeAffectedSkills);
    conflict(!preview.sourceChanged());
    DomainFailure.require(preview.valid(), "INVALID_GRAPH", String.join("；", preview.issues()));
    GraphDocument doc;
    try {
      doc = validator.parse(json.readTree(draft.document()));
    } catch (Exception e) {
      throw new IllegalStateException("Invalid validated draft");
    }
    Map<UUID, String> otherSnapshots = new LinkedHashMap<>();
    if (includeAffectedSkills)
      for (UUID skill : db.affectedSkills(owner())) {
        if (!skill.equals(draft.skillId()) && !db.versions(owner(), skill).isEmpty())
          otherSnapshots.put(skill, materializedSnapshot(skill));
      }
    Set<UUID> oldIds = new HashSet<>();
    catalog.positions(owner(), draft.skillId()).forEach(p -> oldIds.add(p.nodeId()));
    for (var r : catalog.relations(owner()))
      if (oldIds.contains(r.sourceNodeId()) && oldIds.contains(r.targetNodeId()))
        catalog.deleteRelation(owner(), r.id());
    db.clearPositions(owner(), draft.skillId());
    for (var n : doc.nodes()) {
      if (catalog.node(owner(), n.id()) == null)
        catalog.insertNode(
            owner(), n.id(), n.title().strip(), n.nodeType(), n.description(), n.status());
      else
        catalog.updateNode(
            owner(), n.id(), n.title().strip(), n.nodeType(), n.description(), n.status());
    }
    for (var n : doc.nodes())
      catalog.attach(UUID.randomUUID(), owner(), draft.skillId(), n.id(), null, n.displayOrder());
    for (var n : doc.nodes())
      if (n.parentNodeId() != null)
        catalog.move(owner(), draft.skillId(), n.id(), n.parentNodeId(), n.displayOrder());
    for (var r : doc.relations())
      catalog.insertRelation(owner(), r.id(), r.sourceNodeId(), r.targetNodeId(), r.relationType());
    UUID version = UUID.randomUUID();
    db.version(
        version,
        owner(),
        draft.skillId(),
        id,
        db.nextVersion(owner(), draft.skillId()),
        json.valueToTree(doc).toString());
    db.published(owner(), id);
    db.insertKey(owner(), key, id, revision, version);
    for (var before : otherSnapshots.entrySet()) {
      String after = materializedSnapshot(before.getKey());
      if (after.equals(before.getValue())) continue;
      UUID jointDraft = UUID.randomUUID(), jointVersion = UUID.randomUUID();
      db.create(jointDraft, owner(), before.getKey(), after, hash());
      db.version(
          jointVersion,
          owner(),
          before.getKey(),
          jointDraft,
          db.nextVersion(owner(), before.getKey()),
          after);
      db.published(owner(), jointDraft);
      audit.record(
          "GRAPH_DRAFT",
          jointDraft,
          "JOINT_PUBLISHED",
          null,
          Map.of("rootDraftId", id, "rootVersionId", version, "versionId", jointVersion));
    }
    audit.record(
        "GRAPH_DRAFT",
        id,
        "PUBLISHED",
        null,
        Map.of(
            "versionId",
            version,
            "potentiallyAffectedSkills",
            preview.potentiallyAffectedSkills()));
    return version(version);
  }

  private String materializedSnapshot(UUID skill) {
    var graph = skills.graph(skill);
    var nodes =
        graph.nodes().stream()
            .map(
                n ->
                    new GraphDocument.Node(
                        n.id(),
                        n.title(),
                        n.nodeType(),
                        n.description(),
                        n.status(),
                        n.parentNodeId(),
                        n.displayOrder()))
            .sorted(Comparator.comparing(GraphDocument.Node::id))
            .toList();
    var relations =
        graph.relations().stream()
            .map(
                r ->
                    new GraphDocument.Relation(
                        r.id(), r.sourceNodeId(), r.targetNodeId(), r.relationType()))
            .sorted(Comparator.comparing(GraphDocument.Relation::id))
            .toList();
    return json.valueToTree(new GraphDocument(1, nodes, relations)).toString();
  }
}
