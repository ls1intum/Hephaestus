package de.tum.in.www1.hephaestus.gitprovider.user.gitlab;

import de.tum.in.www1.hephaestus.gitprovider.common.gitlab.GitLabProperties;
import de.tum.in.www1.hephaestus.gitprovider.common.gitlab.GitLabSyncConstants;
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
     * Finds or creates a user from GraphQL data (id, username, name, avatarUrl, webUrl).
     * <p>
     * {@code @Transactional} is required because the underlying {@code upsertUser} is a
     * {@code @Modifying} native query.
     */
    @Transactional
    @Nullable
    public User findOrCreateUser(
        String globalId,
        String username,
        @Nullable String name,
        @Nullable String avatarUrl,
        @Nullable String webUrl,
        Long providerId
    ) {
        if (globalId == null || username == null) {
            return null;
        }

        long nativeId;
        try {
            nativeId = GitLabSyncConstants.extractNumericId(globalId);
        } catch (IllegalArgumentException e) {
            log.warn("Skipped user resolution: reason=invalidGlobalId, gid={}", globalId);
            return null;
        }

        String resolvedName = name != null ? name : username;
        String resolvedAvatarUrl = resolveAvatarUrl(avatarUrl);
        String resolvedHtmlUrl = webUrl != null ? webUrl : (gitLabProperties.defaultServerUrl() + "/" + username);

        userRepository.upsertUser(
            nativeId,
            providerId,
            username,
            resolvedName,
            resolvedAvatarUrl,
            resolvedHtmlUrl,
            User.Type.USER.name(),
            null, // email — not available from GraphQL
            null, // createdAt — not in GraphQL user data
            null // updatedAt — not in GraphQL user data
        );

        return userRepository.findByNativeIdAndProviderId(nativeId, providerId).orElse(null);
    }
}
