package de.tum.cit.aet.hephaestus.workspace;

import de.tum.cit.aet.hephaestus.feature.FeatureFlag;
import de.tum.cit.aet.hephaestus.feature.FeatureFlagService;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.workspace.dto.CreateWorkspaceRequestDTO;
import de.tum.cit.aet.hephaestus.workspace.dto.WorkspaceDTO;
import de.tum.cit.aet.hephaestus.workspace.dto.WorkspaceListItemDTO;
import de.tum.cit.aet.hephaestus.workspace.dto.WorkspaceProvidersDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * Workspace registry endpoints that operate outside of a specific workspace slug.
 * These routes stay under the classic /workspaces base path while slugged routes
 * live in {@link WorkspaceController} via {@code @WorkspaceScopedController}.
 */
@RestController
@RequestMapping("/workspaces")
@RequiredArgsConstructor
@Validated
@PreAuthorize("isAuthenticated()")
public class WorkspaceRegistryController {

    private static final String APP_ADMIN_AUTHORITY = "app_admin";

    private final WorkspaceService workspaceService;
    private final WorkspaceQueryService workspaceQueryService;
    private final WorkspaceProvisioningService workspaceProvisioningService;
    private final FeatureFlagService featureFlagService;
    private final WorkspaceProperties workspaceProperties;

    @GetMapping("/providers")
    @Operation(
        summary = "List available workspace creation providers",
        description = "Returns available workspace providers with their configuration. Public endpoint — no authentication required."
    )
    @io.swagger.v3.oas.annotations.security.SecurityRequirements
    @PreAuthorize("permitAll()")
    public ResponseEntity<WorkspaceProvidersDTO> getProviders() {
        return ResponseEntity.ok(workspaceQueryService.getAvailableProviders());
    }

    @PostMapping
    @Operation(summary = "Create a new workspace")
    @ApiResponse(
        responseCode = "201",
        description = "Workspace created",
        content = @Content(schema = @Schema(implementation = WorkspaceDTO.class))
    )
    public ResponseEntity<WorkspaceDTO> createWorkspace(
        @Valid @RequestBody CreateWorkspaceRequestDTO createWorkspaceRequest
    ) {
        // Actor gate (configurable). ADMIN_ONLY (default) restricts creation to instance admins; flip to
        // SELF_SERVICE to let any authenticated user create a workspace. Orthogonal to per-provider
        // availability (the GitLab feature flag below).
        if (workspaceProperties.creationPolicy() == WorkspaceProperties.CreationPolicy.ADMIN_ONLY && !isAppAdmin()) {
            throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.FORBIDDEN,
                "Workspace creation is restricted to instance admins on this deployment"
            );
        }

        if (
            createWorkspaceRequest.kind() == IntegrationKind.GITLAB &&
            !featureFlagService.isEnabled(FeatureFlag.GITLAB_WORKSPACE_CREATION)
        ) {
            throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.FORBIDDEN,
                "GitLab workspace creation is not enabled"
            );
        }

        // For GitLab PAT workspaces, ensure the user has a linked GitLab identity. This provisions
        // the User entity from the account's GitLab IdentityLink, or returns 409 if none is linked.
        if (createWorkspaceRequest.kind() == IntegrationKind.GITLAB) {
            workspaceProvisioningService.ensureAuthenticatedUserExists();
        }

        Workspace workspace = workspaceService.createWorkspaceWithInitialization(createWorkspaceRequest);

        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
            .path("/{workspaceSlug}")
            .buildAndExpand(workspace.getWorkspaceSlug())
            .toUri();

        return ResponseEntity.created(location).body(workspaceQueryService.toWorkspaceDTO(workspace));
    }

    private static boolean isAppAdmin() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return false;
        }
        for (GrantedAuthority authority : authentication.getAuthorities()) {
            if (APP_ADMIN_AUTHORITY.equals(authority.getAuthority())) {
                return true;
            }
        }
        return false;
    }

    @GetMapping
    @Operation(summary = "List all workspaces")
    @ApiResponse(
        responseCode = "200",
        description = "Workspace list",
        content = @Content(array = @ArraySchema(schema = @Schema(implementation = WorkspaceListItemDTO.class)))
    )
    public ResponseEntity<List<WorkspaceListItemDTO>> listWorkspaces() {
        return ResponseEntity.ok(workspaceQueryService.findAccessibleWorkspaceListItems());
    }
}
