package de.tum.cit.aet.hephaestus.workspace.dto;

import de.tum.cit.aet.hephaestus.integration.connection.GitProviderType;
import de.tum.cit.aet.hephaestus.integration.connection.ConnectionConfig;
import de.tum.cit.aet.hephaestus.integration.connection.ConnectionService;
import de.tum.cit.aet.hephaestus.integration.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
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
    @Schema(description = "Integration kind backing this workspace (GITHUB or GITLAB)") String kind,
    @Schema(description = "High-level git provider type for the workspace's SCM connection (null if none bound)")
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
    @NonNull @Schema(description = "Whether a Personal Access Token is configured") Boolean hasPersonalAccessToken,
    @NonNull @Schema(description = "Whether Slack token is configured") Boolean hasSlackToken,
    @NonNull @Schema(description = "Whether Slack signing secret is configured (always false post-#1198)")
    Boolean hasSlackSigningSecret,
    @NonNull
    @Schema(description = "Whether a GitLab webhook has been auto-registered for this workspace")
    Boolean gitlabWebhookRegistered,
    @NonNull @Schema(description = "Whether the practice review feature is enabled") Boolean practicesEnabled,
    @NonNull @Schema(description = "Whether the Pi mentor chat feature is enabled") Boolean mentorEnabled,
    @NonNull @Schema(description = "Whether the achievements system is enabled") Boolean achievementsEnabled,
    @NonNull @Schema(description = "Whether the leaderboard is enabled") Boolean leaderboardEnabled,
    @NonNull @Schema(description = "Whether the league/progression system is enabled") Boolean progressionEnabled,
    @NonNull @Schema(description = "Whether league tiers and rankings are enabled") Boolean leaguesEnabled,
    @NonNull
    @Schema(description = "Whether automatic practice reviews triggered by PR events are enabled")
    Boolean practiceReviewAutoTriggerEnabled,
    @NonNull
    @Schema(description = "Whether manual practice reviews triggered via bot command are enabled")
    Boolean practiceReviewManualTriggerEnabled
) {
    /**
     * Builds a {@link WorkspaceDTO} pulling integration-mode metadata from the
     * {@code Connection} registry rather than from the (now retired) legacy
     * {@code Workspace} columns. Pass the request-scoped {@code ConnectionService}
     * so the lookups can run inside the caller's transaction.
     *
     * <p>{@code installation_linked_at} is derived from the active GitHub App
     * connection's {@code createdAt} — the migration backfilled it from the legacy
     * column for existing rows, and freshly-bound installations stamp it via the
     * standard {@code @CreationTimestamp} on {@link de.tum.cit.aet.hephaestus.integration.connection.Connection}.
     *
     * <p>{@code hasSlackSigningSecret} is always {@code false}: per-workspace signing
     * secrets are dead at runtime (Slack signing-secret comes from the app-global
     * {@code hephaestus.slack.signing-secret}). The DTO field is kept for openapi /
     * webapp compatibility.
     */
    public static WorkspaceDTO from(Workspace workspace, ConnectionService connectionService) {
        long workspaceId = workspace.getId();

        var providerKind = connectionService.findActiveProviderKind(workspaceId);
        var gitHubApp = connectionService.findActiveGitHubAppConfig(workspaceId);
        var gitHubPat = connectionService.findActiveGitHubPatConfig(workspaceId);
        var gitLab = connectionService.findActiveGitLabConfig(workspaceId);
        var slackCfg = connectionService.findSlackNotificationConfig(workspaceId);

        String serverUrl = gitLab.map(ConnectionConfig.GitLabConfig::serverUrl)
            .or(() -> gitHubApp.map(ConnectionConfig.GitHubAppConfig::serverUrl))
            .or(() -> gitHubPat.map(ConnectionConfig.GitHubPatConfig::serverUrl))
            .orElse(null);

        Long installationId = gitHubApp.map(ConnectionConfig.GitHubAppConfig::installationId).orElse(null);

        // installation_linked_at: createdAt on the App connection mirrors what the
        // legacy column tracked. Empty for non-App workspaces.
        Instant installationLinkedAt = connectionService
            .findActive(workspaceId, IntegrationKind.GITHUB)
            .filter(c -> c.getConfig() instanceof ConnectionConfig.GitHubAppConfig)
            .map(c -> c.getCreatedAt())
            .orElse(null);

        boolean hasPat = connectionService
            .findActiveBearerToken(workspaceId, IntegrationKind.GITHUB)
            .map(b -> b.token() != null && !b.token().isEmpty())
            .orElseGet(() -> connectionService
                .findActiveBearerToken(workspaceId, IntegrationKind.GITLAB)
                .map(b -> b.token() != null && !b.token().isEmpty())
                .orElse(false));

        boolean hasSlackToken = connectionService
            .findActiveBearerToken(workspaceId, IntegrationKind.SLACK)
            .map(b -> b.token() != null && !b.token().isEmpty())
            .orElse(false);

        String leaderboardTeam = slackCfg
            .map(s -> s.teamLabel() != null ? s.teamLabel() : s.teamName())
            .orElse(null);
        String leaderboardChannelId = slackCfg.map(ConnectionConfig.SlackConfig::notificationChannelId).orElse(null);

        boolean gitlabWebhookRegistered = gitLab
            .map(c -> c.gitlabWebhookId() != null)
            .orElse(false);

        return new WorkspaceDTO(
            workspaceId,
            workspace.getWorkspaceSlug(),
            workspace.getDisplayName(),
            workspace.getIsPubliclyViewable(),
            workspace.getStatus() != null ? workspace.getStatus().name() : null,
            workspace.getAccountLogin(),
            installationId,
            providerKind.map(Enum::name).orElse(null),
            providerKind.map(IntegrationKind::toGitProviderType).orElse(null),
            serverUrl,
            workspace.getCreatedAt(),
            workspace.getUpdatedAt(),
            installationLinkedAt,
            workspace.getLeaderboardScheduleDay(),
            workspace.getLeaderboardScheduleTime(),
            workspace.getLeaderboardNotificationEnabled(),
            leaderboardTeam,
            leaderboardChannelId,
            hasPat,
            hasSlackToken,
            false,
            gitlabWebhookRegistered,
            workspace.getFeatures().getPracticesEnabled(),
            workspace.getFeatures().getMentorEnabled(),
            workspace.getFeatures().getAchievementsEnabled(),
            workspace.getFeatures().getLeaderboardEnabled(),
            workspace.getFeatures().getProgressionEnabled(),
            workspace.getFeatures().getLeaguesEnabled(),
            workspace.getFeatures().getPracticeReviewAutoTriggerEnabled(),
            workspace.getFeatures().getPracticeReviewManualTriggerEnabled()
        );
    }
}
