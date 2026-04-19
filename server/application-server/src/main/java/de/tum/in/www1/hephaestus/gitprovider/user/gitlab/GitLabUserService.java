package de.tum.in.www1.hephaestus.gitprovider.user.gitlab;

import de.tum.in.www1.hephaestus.gitprovider.common.gitlab.GitLabProperties;
import de.tum.in.www1.hephaestus.gitprovider.common.gitlab.GitLabSyncConstants;
import de.tum.in.www1.hephaestus.gitprovider.common.gitlab.GitLabUserLookup;
import de.tum.in.www1.hephaestus.gitprovider.common.gitlab.dto.GitLabWebhookUser;
import de.tum.in.www1.hephaestus.gitprovider.user.User;
import de.tum.in.www1.hephaestus.gitprovider.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for resolving GitLab users from webhook and GraphQL data.
 * <p>
 * Extracted from {@link de.tum.in.www1.hephaestus.gitprovider.common.gitlab.BaseGitLabProcessor}
 * to allow independent injection and reuse without requiring a processor instance.
 * <p>
 * Not conditional on GitLab being enabled because it is injected into
 * {@link de.tum.in.www1.hephaestus.gitprovider.common.gitlab.BaseGitLabProcessor}
 * which is always available (its subclasses handle webhook/sync events).
 */
@Service
public class GitLabUserService {

    private static final Logger log = LoggerFactory.getLogger(GitLabUserService.class);

    private final UserRepository userRepository;
    private final GitLabProperties gitLabProperties;

    public GitLabUserService(UserRepository userRepository, GitLabProperties gitLabProperties) {
        this.userRepository = userRepository;
        this.gitLabProperties = gitLabProperties;
    }

    /**
     * Resolves a GitLab avatar URL, prepending the server base URL for relative paths.
     * GitLab self-hosted instances return relative paths like {@code /uploads/-/system/user/avatar/123/avatar.png}.
     */
    private String resolveAvatarUrl(@Nullable String avatarUrl) {
        if (avatarUrl == null || avatarUrl.isEmpty()) {
            return "";
        }
        if (avatarUrl.startsWith("/")) {
            return gitLabProperties.defaultServerUrl() + avatarUrl;
        }
        return avatarUrl;
    }

    /**
     * Finds or creates a user from webhook data.
     * <p>
     * Stores the raw GitLab user ID as {@code nativeId} with the given {@code providerId}.
     * Constructs HTML URL from the GitLab server URL and username.
     */
    @Nullable
    public User findOrCreateUser(@Nullable GitLabWebhookUser dto, Long providerId) {
        if (dto == null || dto.id() == null || dto.username() == null) {
            return null;
        }

        long nativeId = dto.id();
        String login = dto.username();
        String name = dto.name() != null ? dto.name() : login;
        String avatarUrl = resolveAvatarUrl(dto.avatarUrl());
        String htmlUrl = gitLabProperties.defaultServerUrl() + "/" + login;

        userRepository.upsertUser(
            nativeId,
            providerId,
            login,
            name,
            avatarUrl,
            htmlUrl,
            User.Type.USER.name(),
            dto.email(),
            null, // createdAt — not in webhook
            null // updatedAt — not in webhook
        );

        return userRepository.findByNativeIdAndProviderId(nativeId, providerId).orElse(null);
    }

    /**
     * Finds or creates a user from GraphQL data.
     * <p>
     * {@code @Transactional} is required because the underlying {@code upsertUser} is a
     * {@code @Modifying} native query. Callers with access to the {@code GitLabUserFields}
     * GraphQL fragment should populate {@link GitLabUserLookup#publicEmail()} so that
     * downstream commit-author resolution can match by email.
     */
    @Transactional
    @Nullable
    public User findOrCreateUser(GitLabUserLookup lookup, Long providerId) {
        if (lookup == null || lookup.globalId() == null || lookup.username() == null) {
            return null;
        }

        long nativeId;
        try {
            nativeId = GitLabSyncConstants.extractNumericId(lookup.globalId());
        } catch (IllegalArgumentException e) {
            log.warn("Skipped user resolution: reason=invalidGlobalId, gid={}", lookup.globalId());
            return null;
        }

        String username = lookup.username();
        String resolvedName = lookup.name() != null ? lookup.name() : username;
        String resolvedAvatarUrl = resolveAvatarUrl(lookup.avatarUrl());
        String resolvedHtmlUrl =
            lookup.webUrl() != null ? lookup.webUrl() : (gitLabProperties.defaultServerUrl() + "/" + username);
        String resolvedEmail = (lookup.publicEmail() != null && !lookup.publicEmail().isBlank())
            ? lookup.publicEmail()
            : null;

        userRepository.upsertUser(
            nativeId,
            providerId,
            username,
            resolvedName,
            resolvedAvatarUrl,
            resolvedHtmlUrl,
            User.Type.USER.name(),
            resolvedEmail,
            null, // createdAt — not in GraphQL user data
            null // updatedAt — not in GraphQL user data
        );

        return userRepository.findByNativeIdAndProviderId(nativeId, providerId).orElse(null);
    }
}
