package de.tum.cit.aet.hephaestus.integration.scm.gitlab.workspace;

import de.tum.cit.aet.hephaestus.workspace.dto.GitLabGroupDTO;
import de.tum.cit.aet.hephaestus.workspace.dto.GitLabPreflightRequestDTO;
import de.tum.cit.aet.hephaestus.workspace.dto.GitLabPreflightResponseDTO;
import de.tum.cit.aet.hephaestus.workspace.dto.GitLabProjectPageDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * GitLab-specific REST endpoints used during the workspace creation wizard. Gated on the
 * {@code GITLAB_WORKSPACE_CREATION} feature flag.
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
            content = @Content(schema = @Schema(implementation = GitLabPreflightResponseDTO.class)))
    @PreAuthorize(
            "@featureFlagService.isEnabled(T(de.tum.cit.aet.hephaestus.feature.FeatureFlag).GITLAB_WORKSPACE_CREATION)")
    public ResponseEntity<GitLabPreflightResponseDTO> gitLabPreflight(
            @Valid @RequestBody GitLabPreflightRequestDTO request) {
        GitLabPreflightResponseDTO result = gitLabPreflightService.validateToken(
                request.personalAccessToken(), request.serverUrl(), request.groupFullPath());
        return ResponseEntity.ok(result);
    }

    @PostMapping("/groups")
    @Operation(summary = "List GitLab groups accessible to a PAT")
    @ApiResponse(
            responseCode = "200",
            description = "Accessible groups",
            content = @Content(array = @ArraySchema(schema = @Schema(implementation = GitLabGroupDTO.class))))
    @PreAuthorize(
            "@featureFlagService.isEnabled(T(de.tum.cit.aet.hephaestus.feature.FeatureFlag).GITLAB_WORKSPACE_CREATION)")
    public ResponseEntity<List<GitLabGroupDTO>> listGitLabGroups(
            @Valid @RequestBody GitLabPreflightRequestDTO request) {
        List<GitLabGroupDTO> groups =
                gitLabPreflightService.listAccessibleGroups(request.personalAccessToken(), request.serverUrl());
        return ResponseEntity.ok(groups);
    }

    @PostMapping("/projects")
    @Operation(summary = "List one page of the projects in a GitLab group and its subgroups that a PAT can read")
    @ApiResponse(
            responseCode = "200",
            description = "One page of projects; nextPage is absent on the last page",
            content = @Content(schema = @Schema(implementation = GitLabProjectPageDTO.class)))
    @PreAuthorize(
            "@featureFlagService.isEnabled(T(de.tum.cit.aet.hephaestus.feature.FeatureFlag).GITLAB_WORKSPACE_CREATION)")
    public ResponseEntity<GitLabProjectPageDTO> listGitLabProjects(
            @Valid @RequestBody GitLabPreflightRequestDTO request,
            @RequestParam(defaultValue = "1") @Positive int page,
            @RequestParam(required = false) @Size(max = 255) @Nullable String search) {
        return ResponseEntity.ok(gitLabPreflightService.listGroupProjects(
                request.personalAccessToken(), request.serverUrl(), request.groupFullPath(), page, search));
    }
}
