package de.tum.in.www1.hephaestus.workspace.dto;

import de.tum.in.www1.hephaestus.workspace.Workspace;
import java.time.Instant;
import org.springframework.lang.NonNull;

public record WorkspaceListItemDTO(
    @NonNull Long id,
    @NonNull String workspaceSlug,
    @NonNull String displayName,
    @NonNull String status,
    @NonNull String accountLogin,
    @NonNull Instant createdAt
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
