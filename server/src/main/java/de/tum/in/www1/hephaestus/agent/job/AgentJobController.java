package de.tum.in.www1.hephaestus.agent.job;

import de.tum.in.www1.hephaestus.workspace.authorization.RequireAtLeastWorkspaceAdmin;
import de.tum.in.www1.hephaestus.workspace.context.WorkspaceContext;
import de.tum.in.www1.hephaestus.workspace.context.WorkspaceScopedController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@WorkspaceScopedController
@RequestMapping("/agent-jobs")
@Tag(name = "Agent Jobs", description = "Workspace-scoped agent job monitoring")
@RequiredArgsConstructor
@Validated
public class AgentJobController {

    private final AgentJobService agentJobService;

    @GetMapping
    @Operation(summary = "List agent jobs for a workspace")
    @ApiResponse(responseCode = "200", description = "Paginated job list")
    @RequireAtLeastWorkspaceAdmin
    public ResponseEntity<Page<AgentJobDTO>> listJobs(
        WorkspaceContext workspaceContext,
        @Parameter(description = "Filter by job status") @RequestParam(required = false) AgentJobStatus status,
        @Parameter(description = "Filter by config ID") @RequestParam(required = false) Long configId,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        int safePage = Math.max(page, 0);
        int pageSize = Math.max(1, Math.min(size, 100));
        Pageable pageable = PageRequest.of(safePage, pageSize, Sort.by("createdAt").descending());
        Page<AgentJobDTO> jobs = agentJobService
            .getJobs(workspaceContext.id(), status, configId, pageable)
            .map(AgentJobDTO::from);
        return ResponseEntity.ok(jobs);
    }

    @GetMapping("/{jobId}")
    @Operation(summary = "Get agent job details")
    @ApiResponse(
        responseCode = "200",
        description = "Job detail returned",
        content = @Content(schema = @Schema(implementation = AgentJobDTO.class))
    )
    @ApiResponse(
        responseCode = "404",
        description = "Job not found in this workspace",
        content = @Content(schema = @Schema(hidden = true))
    )
    @RequireAtLeastWorkspaceAdmin
    public ResponseEntity<AgentJobDTO> getJob(WorkspaceContext workspaceContext, @PathVariable UUID jobId) {
        AgentJob job = agentJobService.getJob(workspaceContext.id(), jobId);
        return ResponseEntity.ok(AgentJobDTO.from(job));
    }

    @PostMapping("/{jobId}/cancel")
    @Operation(summary = "Cancel an agent job")
    @ApiResponse(responseCode = "200", description = "Job cancelled")
    @ApiResponse(
        responseCode = "404",
        description = "Job not found in this workspace",
        content = @Content(schema = @Schema(hidden = true))
    )
    @ApiResponse(
        responseCode = "409",
        description = "Job already in terminal state",
        content = @Content(schema = @Schema(hidden = true))
    )
    @RequireAtLeastWorkspaceAdmin
    public ResponseEntity<AgentJobDTO> cancelJob(WorkspaceContext workspaceContext, @PathVariable UUID jobId) {
        AgentJob job = agentJobService.cancel(workspaceContext.id(), jobId);
        return ResponseEntity.ok(AgentJobDTO.from(job));
    }

    @PostMapping("/{jobId}/delivery/retry")
    @Operation(summary = "Retry delivery for a completed agent job")
    @ApiResponse(responseCode = "200", description = "Delivery retried")
    @ApiResponse(
        responseCode = "404",
        description = "Job not found in this workspace",
        content = @Content(schema = @Schema(hidden = true))
    )
    @ApiResponse(
        responseCode = "409",
        description = "Job not in a retryable state",
        content = @Content(schema = @Schema(hidden = true))
    )
    @RequireAtLeastWorkspaceAdmin
    public ResponseEntity<AgentJobDTO> retryDelivery(WorkspaceContext workspaceContext, @PathVariable UUID jobId) {
        AgentJob job = agentJobService.retryDelivery(workspaceContext.id(), jobId);
        return ResponseEntity.ok(AgentJobDTO.from(job));
    }
}
