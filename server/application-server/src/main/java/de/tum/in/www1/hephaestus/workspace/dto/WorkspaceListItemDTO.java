package de.tum.in.www1.hephaestus.workspace.dto;

import de.tum.in.www1.hephaestus.workspace.Workspace;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import org.springframework.lang.NonNull;

@Schema(description = "Summary information about a workspace for list views")
public record WorkspaceListItemDTO(
    @NonNull @Schema(description = "Unique identifier of the workspace") Long id,
    @NonNull
    @Schema(description = "URL-friendly identifier for the workspace", example = "my-workspace")
    String workspaceSlug,
    @NonNull @Schema(description = "Human-readable name of the workspace") String displayName,
    @NonNull
    @Schema(description = "Current lifecycle status of the workspace (PENDING, ACTIVE, ARCHIVED)")
    String status,
    @NonNull @Schema(description = "GitHub account login associated with this workspace") String accountLogin,
    @NonNull @Schema(description = "Timestamp when the workspace was created") Instant createdAt
) {
    public static WorkspaceListItemDTO from(Workspace workspace) {
        return new WorkspaceListItemDTO(
            workspace.getId(),
            workspace.getWorkspaceSlug(),
            workspace.getDisplayName(),
            workspace.getStatus() != null ? workspace.getStatus().name() : null,
            workspace.getAccountLogin(),
            workspace.getCreatedAt()
        );
    }
}
