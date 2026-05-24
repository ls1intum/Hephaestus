package de.tum.cit.aet.hephaestus.integration.registry;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import java.util.Set;
import org.springframework.lang.Nullable;

/**
 * Sealed per-kind Connection configuration. Persisted as JSONB via
 * {@code ConnectionConfigConverter} (Spring ObjectMapper + AttributeConverter for
 * polymorphic safety — Hibernate's native JSON binding is not used here because
 * sealed-type discriminator semantics aren't proven via {@code @JdbcTypeCode(SqlTypes.JSON)}).
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = ConnectionConfig.GitHubAppConfig.class, name = "GITHUB_APP"),
    @JsonSubTypes.Type(value = ConnectionConfig.GitHubPatConfig.class, name = "GITHUB_PAT"),
    @JsonSubTypes.Type(value = ConnectionConfig.GitLabConfig.class, name = "GITLAB"),
    @JsonSubTypes.Type(value = ConnectionConfig.SlackConfig.class, name = "SLACK"),
    @JsonSubTypes.Type(value = ConnectionConfig.OutlineConfig.class, name = "OUTLINE")
})
public sealed interface ConnectionConfig
    permits ConnectionConfig.GitHubAppConfig,
            ConnectionConfig.GitHubPatConfig,
            ConnectionConfig.GitLabConfig,
            ConnectionConfig.SlackConfig,
            ConnectionConfig.OutlineConfig {

    /** Enabled sync streams (subset of the source's catalog). */
    Set<String> enabledStreams();

    record GitHubAppConfig(
        @Nullable Long installationId,
        @Nullable String orgLogin,
        @Nullable String serverUrl,              // null for github.com, set for GHES
        Set<String> enabledStreams
    ) implements ConnectionConfig {
    }

    record GitHubPatConfig(
        @Nullable String orgLogin,
        @Nullable String serverUrl,
        Set<String> enabledStreams
    ) implements ConnectionConfig {
    }

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
            WHSEC
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
    ) implements ConnectionConfig {
    }

    record OutlineConfig(
        String serverUrl,
        @Nullable String workspaceExternalId,
        Set<String> enabledStreams
    ) implements ConnectionConfig {
    }
}
