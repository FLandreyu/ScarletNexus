package dev.scarletnexus.ai;

import java.util.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/ai/jobs")
public class GenerationJobController {
  private final GenerationJobs jobs;
  private final MoneyLedger.Store costs;

  public GenerationJobController(GenerationJobs jobs, MoneyLedger.Store costs) {
    this.jobs = jobs;
    this.costs = costs;
  }

  public record JobCost(MoneyLedger.Cost cost) {}

  @GetMapping("/{id}/cost")
  public JobCost cost(@PathVariable UUID id) {
    jobs.view(id);
    return new JobCost(costs.cost(dev.scarletnexus.identity.CurrentUser.id(), id));
  }

  @GetMapping
  public List<GenerationJobs.View> list(
      @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
    return jobs.list(page, size);
  }

  @GetMapping("/{id}")
  public GenerationJobs.View get(@PathVariable UUID id) {
    return jobs.view(id);
  }

  public record Recovery(boolean confirmed) {}

  @PostMapping("/{id}/recover")
  public GenerationJobs.View recover(@PathVariable UUID id, @RequestBody Recovery request) {
    return jobs.recover(id, request.confirmed());
  }
}
