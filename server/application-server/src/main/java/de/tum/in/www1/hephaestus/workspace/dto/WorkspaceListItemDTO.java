package de.tum.in.www1.hephaestus.workspace.dto;

import de.tum.in.www1.hephaestus.workspace.Workspace;
import java.time.Instant;

public record WorkspaceListItemDTO(
    Long id,
    String slug,
    String displayName,
    String status,
    String accountLogin,
    Instant createdAt
) {
    public static WorkspaceListItemDTO from(Workspace workspace) {
        return new WorkspaceListItemDTO(
            workspace.getId(),
            workspace.getSlug(),
            workspace.getDisplayName(),
            workspace.getStatus() != null ? workspace.getStatus().name() : null,
            workspace.getAccountLogin(),
            workspace.getCreatedAt()
        );
    }
}
