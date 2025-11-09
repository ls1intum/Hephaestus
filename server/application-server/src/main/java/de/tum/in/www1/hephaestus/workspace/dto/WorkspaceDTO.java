package de.tum.in.www1.hephaestus.workspace.dto;

import de.tum.in.www1.hephaestus.workspace.Workspace;
import java.time.Instant;

public record WorkspaceDTO(
    Long id,
    String slug,
    String displayName,
    Boolean isPubliclyViewable,
    String status,
    String accountLogin,
    Long installationId,
    String gitProviderMode,
    Instant createdAt,
    Instant updatedAt,
    Instant installationLinkedAt,
    Integer leaderboardScheduleDay,
    String leaderboardScheduleTime,
    Boolean leaderboardNotificationEnabled,
    String leaderboardNotificationTeam,
    String leaderboardNotificationChannelId,
    Boolean hasSlackToken,
    Boolean hasSlackSigningSecret
) {
    public static WorkspaceDTO from(Workspace workspace) {
        return new WorkspaceDTO(
            workspace.getId(),
            workspace.getSlug(),
            workspace.getDisplayName(),
            workspace.getIsPubliclyViewable(),
            workspace.getStatus() != null ? workspace.getStatus().name() : null,
            workspace.getAccountLogin(),
            workspace.getInstallationId(),
            workspace.getGitProviderMode() != null ? workspace.getGitProviderMode().name() : null,
            workspace.getCreatedAt(),
            workspace.getUpdatedAt(),
            workspace.getInstallationLinkedAt(),
            workspace.getLeaderboardScheduleDay(),
            workspace.getLeaderboardScheduleTime(),
            workspace.getLeaderboardNotificationEnabled(),
            workspace.getLeaderboardNotificationTeam(),
            workspace.getLeaderboardNotificationChannelId(),
            workspace.getSlackToken() != null && !workspace.getSlackToken().isEmpty(),
            workspace.getSlackSigningSecret() != null && !workspace.getSlackSigningSecret().isEmpty()
        );
    }
}
