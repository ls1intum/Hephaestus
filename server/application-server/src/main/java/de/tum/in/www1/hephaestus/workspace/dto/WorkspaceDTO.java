package de.tum.in.www1.hephaestus.workspace.dto;

import de.tum.in.www1.hephaestus.workspace.GitProviderType;
import de.tum.in.www1.hephaestus.workspace.Workspace;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import org.springframework.lang.NonNull;

@Schema(description = "Complete workspace information including configuration and settings")
public record WorkspaceDTO(
    @NonNull @Schema(description = "Unique identifier of the workspace") Long id,
    @NonNull
    @Schema(description = "URL-friendly identifier for the workspace", example = "my-workspace")
    String workspaceSlug,
    @NonNull @Schema(description = "Human-readable name of the workspace") String displayName,
    @NonNull
    @Schema(description = "Whether the workspace is publicly viewable without authentication")
    Boolean isPubliclyViewable,
    @NonNull
    @Schema(description = "Current lifecycle status of the workspace (PENDING, ACTIVE, ARCHIVED)")
    String status,
    @NonNull @Schema(description = "Git provider account login associated with this workspace") String accountLogin,
    @Schema(description = "GitHub App installation ID, if linked") Long installationId,
    @Schema(description = "Git provider mode (PAT_ORG, GITHUB_APP_INSTALLATION, GITLAB_PAT)") String gitProviderMode,
    @NonNull
    @Schema(description = "High-level git provider type derived from the authentication mode")
    GitProviderType providerType,
    @Schema(description = "Custom server URL for self-hosted instances (null for cloud defaults)") String serverUrl,
    @NonNull @Schema(description = "Timestamp when the workspace was created") Instant createdAt,
    @NonNull @Schema(description = "Timestamp when the workspace was last updated") Instant updatedAt,
    @Schema(description = "Timestamp when the GitHub App installation was linked") Instant installationLinkedAt,
    @Schema(description = "Day of week for leaderboard notifications (1=Monday, 7=Sunday)", example = "1")
    Integer leaderboardScheduleDay,
    @Schema(description = "Time for leaderboard notifications in HH:mm format", example = "09:00")
    String leaderboardScheduleTime,
    @Schema(description = "Whether leaderboard notifications are enabled") Boolean leaderboardNotificationEnabled,
    @Schema(description = "Team name for leaderboard notifications") String leaderboardNotificationTeam,
    @Schema(description = "Slack channel ID for leaderboard notifications") String leaderboardNotificationChannelId,
    @NonNull @Schema(description = "Whether Slack token is configured") Boolean hasSlackToken,
    @NonNull @Schema(description = "Whether Slack signing secret is configured") Boolean hasSlackSigningSecret
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
            workspace.getProviderType(),
            workspace.getServerUrl(),
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
