package de.tum.in.www1.hephaestus.workspace;

import de.tum.in.www1.hephaestus.workspace.dto.CreateWorkspaceRequestDTO;
import de.tum.in.www1.hephaestus.workspace.dto.WorkspaceDTO;
import de.tum.in.www1.hephaestus.workspace.dto.WorkspaceListItemDTO;
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
@RequestMapping({ "/workspaces", "/workspaces/" })
@RequiredArgsConstructor
@Validated
public class WorkspaceRegistryController {

    private final WorkspaceService workspaceService;

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
        Workspace workspace = workspaceService.createWorkspace(
            createWorkspaceRequest.workspaceSlug(),
            createWorkspaceRequest.displayName(),
            createWorkspaceRequest.accountLogin(),
            createWorkspaceRequest.accountType(),
            createWorkspaceRequest.ownerUserId()
        );

        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
            .path("/{workspaceSlug}")
            .buildAndExpand(workspace.getWorkspaceSlug())
            .toUri();

        return ResponseEntity.created(location).body(WorkspaceDTO.from(workspace));
    }

    @GetMapping
    @Operation(summary = "List all workspaces")
    @ApiResponse(
        responseCode = "200",
        description = "Workspace list",
        content = @Content(array = @ArraySchema(schema = @Schema(implementation = WorkspaceListItemDTO.class)))
    )
    public ResponseEntity<List<WorkspaceListItemDTO>> listWorkspaces() {
        List<WorkspaceListItemDTO> workspaces = workspaceService
            .listAllWorkspaces()
            .stream()
            .map(WorkspaceListItemDTO::from)
            .toList();
        return ResponseEntity.ok(workspaces);
    }
}
