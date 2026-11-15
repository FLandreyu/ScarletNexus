package dev.scarletnexus.common;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/system")
public class SystemController {
  private final SystemMapper mapper;

  public SystemController(SystemMapper mapper) {
    this.mapper = mapper;
  }

  @Operation(summary = "查询真实数据库连接与工程阶段；不返回连接凭据")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Database connection and migration verified",
        content = @Content(schema = @Schema(implementation = SystemStatus.class))),
    @ApiResponse(
        responseCode = "503",
        description = "Database unavailable",
        content = @Content(schema = @Schema(implementation = ApiError.class)))
  })
  @GetMapping("/status")
  public SystemStatus status() {
    return new SystemStatus("scarlet-nexus", "UP", "UP", mapper.schemaVersion(), "P1", false);
  }

  public record SystemStatus(
      String service,
      String status,
      String database,
      String schemaVersion,
      String phase,
      boolean aiConfigured) {}
}
