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
        @JsonSubTypes.Type(value = ConnectionConfig.OidcLoginConfig.class, name = "OIDC_LOGIN"),
    }
)
public sealed interface ConnectionConfig
    permits
        ConnectionConfig.GitHubAppConfig,
        ConnectionConfig.GitHubPatConfig,
        ConnectionConfig.GitLabConfig,
        ConnectionConfig.SlackConfig,
        ConnectionConfig.OutlineConfig,
        ConnectionConfig.OidcLoginConfig
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

    /**
     * Workspace-scoped OIDC login provider configuration. Backs Connection rows of
     * {@code kind = OIDC_LOGIN_GITHUB | OIDC_LOGIN_GITLAB} in family {@code IDENTITY}.
     * The relying-party {@code client_secret} lives in {@code Connection.credentialsEncrypted}
     * as an {@code OAuthClientSecret} {@code CredentialBundle}, AAD-bound per ADR 0014.
     *
     * <p>Identity Connections never participate in SCM sync; {@link #enabledStreams()}
     * returns an empty set.
     *
     * @param issuerUrl  Discovery base URL of the IdP (e.g. {@code https://gitlab.lrz.de}).
     *                   {@code /.well-known/openid-configuration} is fetched from here at
     *                   registration time via the SSRF-protected probe.
     * @param scopes     OAuth scopes to request (subset of what the IdP supports).
     * @param displayName  Human-friendly label rendered in the SPA login picker.
     */
    record OidcLoginConfig(
        String issuerUrl,
        Set<String> scopes,
        String displayName
    ) implements ConnectionConfig {
        @Override
        public Set<String> enabledStreams() {
            return Set.of();
        }
    }
}
