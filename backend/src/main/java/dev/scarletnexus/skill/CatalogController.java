package dev.scarletnexus.skill;

import dev.scarletnexus.skill.CatalogModels.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class CatalogController {
  private final CatalogService catalog;

  public CatalogController(CatalogService catalog) {
    this.catalog = catalog;
  }

  public record SkillWrite(
      @NotBlank @Size(max = 120) String name,
      @NotNull @Size(max = 4000) String description,
      @NotNull @Size(max = 80) String category,
      @NotNull @Pattern(regexp = "ACTIVE|ARCHIVED") String status) {}

  public record NodeWrite(
      @NotBlank @Size(max = 160) String title,
      @NotNull @Pattern(regexp = "CATEGORY|CONCEPT|PRACTICE") String nodeType,
      @NotNull @Size(max = 8000) String description,
      @NotNull @Pattern(regexp = "ACTIVE|ARCHIVED") String status) {}

  public record PositionWrite(UUID parentNodeId, @Min(0) int displayOrder) {}

  public record AttachWrite(@NotNull UUID nodeId, UUID parentNodeId, @Min(0) int displayOrder) {}

  public record RelationWrite(
      @NotNull UUID sourceNodeId,
      @NotNull UUID targetNodeId,
      @NotNull @Pattern(regexp = "PREREQUISITE_OF|RELATED_TO") String relationType) {}

  @GetMapping("/skills")
  public Page<Skill> skills(
      @RequestParam(defaultValue = "") String q,
      @RequestParam(defaultValue = "ACTIVE") String status,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return catalog.skills(q, status, page, size);
  }

  @GetMapping("/skills/{id}")
  public Skill skill(@PathVariable UUID id) {
    return catalog.skill(id);
  }

  @PostMapping("/skills")
  public ResponseEntity<Skill> createSkill(@Valid @RequestBody SkillWrite value) {
    Skill skill = catalog.saveSkill(null, value);
    return ResponseEntity.created(URI.create("/api/v1/skills/" + skill.id())).body(skill);
  }

  @PatchMapping("/skills/{id}")
  public Skill updateSkill(@PathVariable UUID id, @Valid @RequestBody SkillWrite value) {
    return catalog.saveSkill(id, value);
  }

  @GetMapping("/knowledge/nodes")
  public Page<Node> nodes(
      @RequestParam(defaultValue = "") String q,
      @RequestParam(defaultValue = "ACTIVE") String status,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return catalog.nodes(q, status, page, size);
  }

  @GetMapping("/knowledge/nodes/{id}")
  public Node node(@PathVariable UUID id) {
    return catalog.node(id);
  }

  @PostMapping("/knowledge/nodes")
  public ResponseEntity<Node> createNode(@Valid @RequestBody NodeWrite value) {
    Node node = catalog.saveNode(null, value);
    return ResponseEntity.created(URI.create("/api/v1/knowledge/nodes/" + node.id())).body(node);
  }

  @PatchMapping("/knowledge/nodes/{id}")
  public Node updateNode(@PathVariable UUID id, @Valid @RequestBody NodeWrite value) {
    return catalog.saveNode(id, value);
  }

  @GetMapping("/skills/{id}/graph")
  public Graph graph(@PathVariable UUID id) {
    return catalog.graph(id);
  }

  @PostMapping("/skills/{id}/nodes")
  public ResponseEntity<Position> attach(
      @PathVariable UUID id, @Valid @RequestBody AttachWrite value) {
    return ResponseEntity.status(201)
        .body(
            catalog.place(
                id,
                value.nodeId(),
                new PositionWrite(value.parentNodeId(), value.displayOrder()),
                true));
  }

  @PatchMapping("/skills/{id}/nodes/{node}")
  public Position move(
      @PathVariable UUID id, @PathVariable UUID node, @Valid @RequestBody PositionWrite value) {
    return catalog.place(id, node, value, false);
  }

  @DeleteMapping("/skills/{id}/nodes/{node}")
  public ResponseEntity<Void> detach(@PathVariable UUID id, @PathVariable UUID node) {
    catalog.detach(id, node);
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/knowledge/relations")
  public List<Relation> relations() {
    return catalog.relations();
  }

  @PostMapping("/knowledge/relations")
  public ResponseEntity<Relation> createRelation(@Valid @RequestBody RelationWrite value) {
    return ResponseEntity.status(201).body(catalog.saveRelation(null, value));
  }

  @PatchMapping("/knowledge/relations/{id}")
  public Relation updateRelation(@PathVariable UUID id, @Valid @RequestBody RelationWrite value) {
    return catalog.saveRelation(id, value);
  }

  @DeleteMapping("/knowledge/relations/{id}")
  public ResponseEntity<Void> deleteRelation(@PathVariable UUID id) {
    catalog.deleteRelation(id);
    return ResponseEntity.noContent().build();
  }
}
