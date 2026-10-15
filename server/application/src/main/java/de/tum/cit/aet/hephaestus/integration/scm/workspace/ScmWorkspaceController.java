package de.tum.cit.aet.hephaestus.integration.scm.workspace;

import de.tum.cit.aet.hephaestus.core.AuditExempt;
import de.tum.cit.aet.hephaestus.integration.scm.domain.team.TeamInfoDTO;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.UserTeamsDTO;
import de.tum.cit.aet.hephaestus.workspace.authorization.RequireAtLeastWorkspaceAdmin;
import de.tum.cit.aet.hephaestus.workspace.context.WorkspaceContext;
import de.tum.cit.aet.hephaestus.workspace.context.WorkspaceScopedController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.net.URI;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@WorkspaceScopedController
@Tag(name = "workspace")
@RequiredArgsConstructor
@Validated
public class ScmWorkspaceController {
    private final WorkspaceRepositoryMonitorService workspaceRepositoryMonitorService;
    private final WorkspaceTeamLabelService workspaceTeamLabelService;

    @GetMapping("/repositories")
    @Operation(summary = "List repositories monitored by a workspace")
    @ApiResponse(
            responseCode = "200",
            description = "Repository list",
            content = @Content(array = @ArraySchema(schema = @Schema(implementation = String.class))))
    public ResponseEntity<List<String>> getRepositoriesToMonitor(WorkspaceContext workspaceContext) {
        var repositories = workspaceRepositoryMonitorService.getMonitoredRepositories(workspaceContext).stream()
                .sorted()
                .toList();
        return ResponseEntity.ok(repositories);
    }

    @PostMapping("/repositories")
    @Operation(summary = "Add a repository to a workspace monitor list")
    @RequireAtLeastWorkspaceAdmin
    @AuditExempt(
            reason = "which repositories are synced is ingestion scope, not a control that gates"
                    + " delivery; not recorded")
    public ResponseEntity<Void> addRepositoryToMonitor(
            WorkspaceContext workspaceContext, @RequestParam String nameWithOwner) {
        workspaceRepositoryMonitorService.addRepositoryToMonitor(workspaceContext, nameWithOwner);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().build().toUri();
        return ResponseEntity.created(location).build();
    }

    @DeleteMapping("/repositories")
    @Operation(summary = "Remove a repository from a workspace monitor list")
    @RequireAtLeastWorkspaceAdmin
    @AuditExempt(
            reason = "which repositories are synced is ingestion scope, not a control that gates"
                    + " delivery; not recorded")
    public ResponseEntity<Void> removeRepositoryToMonitor(
            WorkspaceContext workspaceContext, @RequestParam String nameWithOwner) {
        workspaceRepositoryMonitorService.removeRepositoryFromMonitor(workspaceContext, nameWithOwner);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/users")
    @Operation(summary = "List workspace users and the teams they belong to")
    public ResponseEntity<List<UserTeamsDTO>> getUsersWithTeams(WorkspaceContext workspaceContext) {
        return ResponseEntity.ok(workspaceTeamLabelService.getUsersWithTeams(workspaceContext));
    }

    @PostMapping("/teams/{teamId}/labels/{repositoryId}/{label}")
    @Operation(summary = "Add a repository label to a team")
    @RequireAtLeastWorkspaceAdmin
    @AuditExempt(reason = "team label filter is a reporting view, not configuration that gates delivery")
    public ResponseEntity<TeamInfoDTO> addLabelToTeam(
            WorkspaceContext workspaceContext,
            @PathVariable Long teamId,
            @PathVariable Long repositoryId,
            @PathVariable String label) {
        return workspaceTeamLabelService
                .addLabelToTeam(workspaceContext, teamId, repositoryId, label)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @DeleteMapping("/teams/{teamId}/labels/{labelId}")
    @Operation(summary = "Remove a repository label from a team")
    @RequireAtLeastWorkspaceAdmin
    @AuditExempt(reason = "team label filter is a reporting view, not configuration that gates delivery")
    public ResponseEntity<TeamInfoDTO> removeLabelFromTeam(
            WorkspaceContext workspaceContext, @PathVariable Long teamId, @PathVariable Long labelId) {
        return workspaceTeamLabelService
                .removeLabelFromTeam(workspaceContext, teamId, labelId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
