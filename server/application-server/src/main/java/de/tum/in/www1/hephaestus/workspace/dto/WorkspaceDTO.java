package de.tum.in.www1.hephaestus.workspace.dto;

import de.tum.in.www1.hephaestus.workspace.Workspace;
import java.time.Instant;
import org.springframework.lang.NonNull;

public record WorkspaceDTO(
    @NonNull Long id,
    @NonNull String workspaceSlug,
    @NonNull String displayName,
    @NonNull Boolean isPubliclyViewable,
    @NonNull String status,
    @NonNull String accountLogin,
    Long installationId,
    String gitProviderMode,
    @NonNull Instant createdAt,
    @NonNull Instant updatedAt,
    Instant installationLinkedAt,
    Integer leaderboardScheduleDay,
    String leaderboardScheduleTime,
    Boolean leaderboardNotificationEnabled,
    String leaderboardNotificationTeam,
    String leaderboardNotificationChannelId,
    @NonNull Boolean hasSlackToken,
    @NonNull Boolean hasSlackSigningSecret
) {
    public static WorkspaceDTO from(Workspace workspace) {
        return new WorkspaceDTO(
            workspace.getId(),
            workspace.getWorkspaceSlug(),
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
