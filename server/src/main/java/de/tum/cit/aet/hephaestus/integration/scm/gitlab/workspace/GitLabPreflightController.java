package de.tum.cit.aet.hephaestus.integration.scm.gitlab.workspace;

import de.tum.cit.aet.hephaestus.workspace.dto.GitLabGroupDTO;
import de.tum.cit.aet.hephaestus.workspace.dto.GitLabPreflightRequestDTO;
import de.tum.cit.aet.hephaestus.workspace.dto.GitLabPreflightResponseDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * GitLab-specific REST endpoints used during the workspace creation wizard:
 * {@code POST /workspaces/gitlab/preflight} validates a PAT, {@code POST /workspaces/gitlab/groups}
 * lists accessible groups for one. Split from {@code WorkspaceRegistryController} so the
 * workspace module no longer imports {@link GitLabPreflightService}; the routes keep the
 * existing {@code /workspaces/gitlab/*} URL space for API stability.
 *
 * <p>Both endpoints are gated by the {@code GITLAB_WORKSPACE_CREATION} feature flag via
 * {@code @PreAuthorize} — identical to the previous protection on the merged controller.
 */
@RestController
@RequestMapping("/workspaces/gitlab")
@Validated
@PreAuthorize("isAuthenticated()")
public class GitLabPreflightController {

    private final GitLabPreflightService gitLabPreflightService;

    public GitLabPreflightController(GitLabPreflightService gitLabPreflightService) {
        this.gitLabPreflightService = gitLabPreflightService;
    }

    @PostMapping("/preflight")
    @Operation(summary = "Validate a GitLab PAT before workspace creation")
    @ApiResponse(
        responseCode = "200",
        description = "Validation result",
        content = @Content(schema = @Schema(implementation = GitLabPreflightResponseDTO.class))
    )
    @PreAuthorize(
        "@featureFlagService.isEnabled(T(de.tum.cit.aet.hephaestus.feature.FeatureFlag).GITLAB_WORKSPACE_CREATION)"
    )
    public ResponseEntity<GitLabPreflightResponseDTO> gitLabPreflight(
        @Valid @RequestBody GitLabPreflightRequestDTO request
    ) {
        GitLabPreflightResponseDTO result = gitLabPreflightService.validateToken(
            request.personalAccessToken(),
            request.serverUrl(),
            request.groupFullPath()
        );
        return ResponseEntity.ok(result);
    }

    @PostMapping("/groups")
    @Operation(summary = "List GitLab groups accessible to a PAT")
    @ApiResponse(
        responseCode = "200",
        description = "Accessible groups",
        content = @Content(array = @ArraySchema(schema = @Schema(implementation = GitLabGroupDTO.class)))
    )
    @PreAuthorize(
        "@featureFlagService.isEnabled(T(de.tum.cit.aet.hephaestus.feature.FeatureFlag).GITLAB_WORKSPACE_CREATION)"
    )
    public ResponseEntity<List<GitLabGroupDTO>> listGitLabGroups(@Valid @RequestBody GitLabPreflightRequestDTO request) {
        List<GitLabGroupDTO> groups = gitLabPreflightService.listAccessibleGroups(
            request.personalAccessToken(),
            request.serverUrl()
        );
        return ResponseEntity.ok(groups);
    }
}
