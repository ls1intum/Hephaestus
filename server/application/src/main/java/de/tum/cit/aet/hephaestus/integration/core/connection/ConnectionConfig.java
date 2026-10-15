package de.tum.cit.aet.hephaestus.integration.core.connection;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;

/**
 * Sealed per-kind Connection configuration. Persisted as JSONB via Hibernate 7's
 * native {@code @JdbcTypeCode(SqlTypes.JSON)} on {@code Connection.config}; Jackson 3
 * deserialises the discriminator below back into the right record subtype.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = ConnectionConfig.GitHubAppConfig.class, name = "GITHUB_APP"),
    @JsonSubTypes.Type(value = ConnectionConfig.GitHubPatConfig.class, name = "GITHUB_PAT"),
    @JsonSubTypes.Type(value = ConnectionConfig.GitLabConfig.class, name = "GITLAB"),
    @JsonSubTypes.Type(value = ConnectionConfig.SlackConfig.class, name = "SLACK"),
    @JsonSubTypes.Type(value = ConnectionConfig.OutlineConfig.class, name = "OUTLINE"),
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
            @Nullable String serverUrl, // null for github.com, set for GHES
            Set<String> enabledStreams)
            implements ConnectionConfig {}

    record GitHubPatConfig(
            @Nullable String orgLogin, @Nullable String serverUrl, Set<String> enabledStreams)
            implements ConnectionConfig {}

    /** GitLab — supports both legacy plaintext token verifier and 19.0+ HMAC whsec_*. */
    record GitLabConfig(
            @Nullable String serverUrl,
            @Nullable Long gitlabGroupId,
            @Nullable Long gitlabWebhookId,
            SigningMode signingMode,
            Set<String> enabledStreams,
            @Nullable GitLabTokenMetadata tokenMetadata,
            GitLabProjectExclusions projectExclusions)
            implements ConnectionConfig {
        public GitLabConfig {
            projectExclusions = Objects.requireNonNullElseGet(projectExclusions, GitLabProjectExclusions::empty);
        }

        public GitLabConfig(
                @Nullable String serverUrl,
                @Nullable Long gitlabGroupId,
                @Nullable Long gitlabWebhookId,
                SigningMode signingMode,
                Set<String> enabledStreams,
                @Nullable GitLabTokenMetadata tokenMetadata) {
            this(
                    serverUrl,
                    gitlabGroupId,
                    gitlabWebhookId,
                    signingMode,
                    enabledStreams,
                    tokenMetadata,
                    GitLabProjectExclusions.empty());
        }

        public GitLabConfig withTokenMetadata(@Nullable GitLabTokenMetadata metadata) {
            return new GitLabConfig(
                    serverUrl,
                    gitlabGroupId,
                    gitlabWebhookId,
                    signingMode,
                    enabledStreams,
                    metadata,
                    projectExclusions);
        }

        public GitLabConfig withProjectExclusions(GitLabProjectExclusions exclusions) {
            return new GitLabConfig(
                    serverUrl, gitlabGroupId, gitlabWebhookId, signingMode, enabledStreams, tokenMetadata, exclusions);
        }

        public enum SigningMode {
            PLAINTEXT,
            WHSEC,
        }

        /**
         * Returns a copy with {@link #gitlabWebhookId} replaced, stamped after webhook
         * registration/adoption. Pair with {@code connectionService.updateConfig(...)} to
         * persist the swap atomically.
         */
        public GitLabConfig withGitlabWebhookId(@Nullable Long webhookId) {
            return new GitLabConfig(
                    serverUrl, gitlabGroupId, webhookId, signingMode, enabledStreams, tokenMetadata, projectExclusions);
        }

        /**
         * Returns a copy with {@link #gitlabGroupId} replaced, for when the GraphQL group
         * lookup resolves the numeric id on a workspace that only carried the group path.
         */
        public GitLabConfig withGitlabGroupId(@Nullable Long groupId) {
            return new GitLabConfig(
                    serverUrl, groupId, gitlabWebhookId, signingMode, enabledStreams, tokenMetadata, projectExclusions);
        }
    }

    /**
     * Explicit stops survive mirror deletion. Known projects are identified only by native ID;
     * a legacy monitor without a known source ID stops its path until a canonical read binds the ID.
     */
    record GitLabProjectExclusions(Set<Long> nativeIds, Set<String> unresolvedPaths) {
        public GitLabProjectExclusions {
            nativeIds = Set.copyOf(Objects.requireNonNullElse(nativeIds, Set.of()));
            unresolvedPaths = Objects.requireNonNullElse(unresolvedPaths, Set.<String>of()).stream()
                    .map(GitLabProjectExclusions::normalizePath)
                    .collect(Collectors.toUnmodifiableSet());
            if (nativeIds.stream().anyMatch(id -> id <= 0) || unresolvedPaths.contains("")) {
                throw new IllegalArgumentException("Project exclusions require positive IDs or nonblank paths");
            }
        }

        public static GitLabProjectExclusions empty() {
            return new GitLabProjectExclusions(Set.of(), Set.of());
        }

        public boolean excludes(@Nullable Long nativeId, String path) {
            return (nativeId != null && nativeIds.contains(nativeId)) || unresolvedPaths.contains(normalizePath(path));
        }

        public GitLabProjectExclusions exclude(@Nullable Long nativeId, String path) {
            Set<Long> ids = new HashSet<>(nativeIds);
            Set<String> paths = new HashSet<>(unresolvedPaths);
            if (nativeId == null) paths.add(normalizePath(path));
            else ids.add(nativeId);
            return new GitLabProjectExclusions(ids, paths);
        }

        public GitLabProjectExclusions include(@Nullable Long nativeId, String path) {
            Set<Long> ids = new HashSet<>(nativeIds);
            Set<String> paths = new HashSet<>(unresolvedPaths);
            if (nativeId != null) ids.remove(nativeId);
            paths.remove(normalizePath(path));
            return new GitLabProjectExclusions(ids, paths);
        }

        public GitLabProjectExclusions resolvePath(long nativeId, String path) {
            return excludes(null, path) ? include(null, path).exclude(nativeId, path) : this;
        }

        private static String normalizePath(String path) {
            return path.trim().toLowerCase(Locale.ROOT);
        }
    }

    record GitLabTokenMetadata(@Nullable LocalDate expiresAt, Instant checkedAt) {}

    /** Slack bot identity, enabled streams, and message-retention configuration. */
    record SlackConfig(
            @Nullable String teamId,
            @Nullable String teamName,
            @Nullable Integer retentionDays,
            Set<String> enabledStreams)
            implements ConnectionConfig {
        /** Default bounded-retention window for ingested Slack messages, in days. */
        public static final int DEFAULT_RETENTION_DAYS = 30;

        /** The configured retention window, or {@link #DEFAULT_RETENTION_DAYS} when unset. */
        public int retentionDaysOrDefault() {
            return retentionDays != null ? retentionDays : DEFAULT_RETENTION_DAYS;
        }
    }

    /**
     * Outline — the server host and (when the change-notification subscription is registered)
     * its id plus signing secret. Which collections are mirrored is NOT config: the
     * {@code outline_collection} registry is the single source of truth, populated post-connect
     * through the admin surface.
     *
     * <p>{@code serverUrl} is validated against the SSRF guard before any request is made.
     * The webhook fields stay {@code null} until a change-notification subscription is registered.
     */
    record OutlineConfig(
            @Nullable String serverUrl,
            @Nullable String webhookSubscriptionId,
            @Nullable String webhookSecret,
            Set<String> enabledStreams)
            implements ConnectionConfig {
        /**
         * Returns a copy with the change-notification subscription id and signing secret
         * replaced, stamped after the subscription is registered. Pair with
         * {@code connectionService.updateConfig(...)} to persist the swap atomically.
         */
        public OutlineConfig withWebhookSubscription(@Nullable String subscriptionId, @Nullable String signingSecret) {
            return new OutlineConfig(serverUrl, subscriptionId, signingSecret, enabledStreams);
        }
    }
}
