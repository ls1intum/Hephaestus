package de.tum.cit.aet.hephaestus.integration.core.connection;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import java.util.Set;
import org.springframework.lang.Nullable;

/**
 * Sealed per-kind Connection configuration. Persisted as JSONB via Hibernate 7's
 * native {@code @JdbcTypeCode(SqlTypes.JSON)} on {@code Connection.config}; Jackson 3
 * deserialises the discriminator below back into the right record subtype.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes(
    {
        @JsonSubTypes.Type(value = ConnectionConfig.GitHubAppConfig.class, name = "GITHUB_APP"),
        @JsonSubTypes.Type(value = ConnectionConfig.GitHubPatConfig.class, name = "GITHUB_PAT"),
        @JsonSubTypes.Type(value = ConnectionConfig.GitLabConfig.class, name = "GITLAB"),
        @JsonSubTypes.Type(value = ConnectionConfig.SlackConfig.class, name = "SLACK"),
        @JsonSubTypes.Type(value = ConnectionConfig.OutlineConfig.class, name = "OUTLINE"),
    }
)
public sealed interface ConnectionConfig
    permits
        ConnectionConfig.GitHubAppConfig,
        ConnectionConfig.GitHubPatConfig,
        ConnectionConfig.GitLabConfig,
        ConnectionConfig.SlackConfig,
        ConnectionConfig.OutlineConfig
{
    /** Enabled sync streams (subset of the source's catalog). */
    Set<String> enabledStreams();

    record GitHubAppConfig(
        @Nullable Long installationId,
        @Nullable String orgLogin,
        @Nullable String serverUrl, // null for github.com, set for GHES
        Set<String> enabledStreams
    ) implements ConnectionConfig {}

    record GitHubPatConfig(
        @Nullable String orgLogin,
        @Nullable String serverUrl,
        Set<String> enabledStreams
    ) implements ConnectionConfig {}

    /** GitLab — supports both legacy plaintext token verifier and 19.0+ HMAC whsec_*. */
    record GitLabConfig(
        String serverUrl,
        @Nullable Long gitlabGroupId,
        @Nullable Long gitlabWebhookId,
        SigningMode signingMode,
        Set<String> enabledStreams
    ) implements ConnectionConfig {
        public enum SigningMode {
            PLAINTEXT,
            WHSEC,
        }

        /**
         * Returns a copy with {@link #gitlabWebhookId} replaced. Used after webhook
         * registration / adoption to stamp the new id without mutating the persisted
         * record. Caller pairs this with {@code connectionService.updateConfig(...)} to
         * persist the swap atomically.
         */
        public GitLabConfig withGitlabWebhookId(@Nullable Long webhookId) {
            return new GitLabConfig(serverUrl, gitlabGroupId, webhookId, signingMode, enabledStreams);
        }

        /**
         * Returns a copy with {@link #gitlabGroupId} replaced. Used when the GraphQL
         * group lookup resolves the numeric id on a workspace that was only carrying
         * the human-readable group path.
         */
        public GitLabConfig withGitlabGroupId(@Nullable Long groupId) {
            return new GitLabConfig(serverUrl, groupId, gitlabWebhookId, signingMode, enabledStreams);
        }
    }

    /**
     * Slack — single record holds bot identity AND notification config (D24 — resolves
     * the prior plan's collision between separate SlackBotConfig + SlackNotificationConfig
     * rows under {@code UNIQUE(workspace_id, kind)}).
     */
    record SlackConfig(
        @Nullable String teamId,
        @Nullable String teamName,
        @Nullable String notificationChannelId,
        @Nullable String teamLabel,
        Set<String> enabledStreams
    ) implements ConnectionConfig {}

    record OutlineConfig(
        String serverUrl,
        @Nullable String workspaceExternalId,
        Set<String> enabledStreams
    ) implements ConnectionConfig {}
}
