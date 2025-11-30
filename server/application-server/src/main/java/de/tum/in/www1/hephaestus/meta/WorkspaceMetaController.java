package de.tum.in.www1.hephaestus.meta;

import de.tum.in.www1.hephaestus.workspace.context.WorkspaceContext;
import de.tum.in.www1.hephaestus.workspace.context.WorkspaceScopedController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Controller for workspace metadata and contributor information.
 * Provides read-only access to aggregated workspace statistics and contributor lists.
 */
@WorkspaceScopedController
@RequestMapping("/meta")
@RequiredArgsConstructor
@Tag(name = "Workspace Metadata", description = "Workspace statistics and contributor information")
public class WorkspaceMetaController {

    private final MetaService metaService;

    /**
     * Get aggregated metadata for the workspace.
     *
     * @param workspaceContext the resolved workspace context
     * @return workspace metadata including statistics
     */
    @GetMapping
    @Operation(summary = "Get workspace metadata", description = "Returns aggregated statistics for the workspace")
    public ResponseEntity<MetaDataDTO> getWorkspaceMeta(WorkspaceContext workspaceContext) {
        return ResponseEntity.ok(metaService.getWorkspaceMetaData(workspaceContext));
    }

    /**
     * List all contributors in the workspace.
     *
     * @param workspaceContext the resolved workspace context
     * @return list of contributors with their contribution metrics
     */
    @GetMapping("/contributors")
    @Operation(
        summary = "List workspace contributors",
        description = "Returns all users who have contributed to the workspace"
    )
    public ResponseEntity<List<ContributorDTO>> listWorkspaceContributors(WorkspaceContext workspaceContext) {
        return ResponseEntity.ok(metaService.getWorkspaceContributors(workspaceContext));
    }
}
