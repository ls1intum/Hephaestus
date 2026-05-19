package de.tum.in.www1.hephaestus.gitprovider.common.gitlab;

import org.springframework.lang.Nullable;

/**
 * Parameter object for looking up or creating a GitLab user from GraphQL data.
 * <p>
 * Wraps the identity + profile fields needed to persist a user row so that
 * {@code findOrCreateUser} can take a single payload instead of a long
 * parameter list. {@code publicEmail} is optional because only some callers
 * have access to the {@code GitLabUserFields} GraphQL fragment.
 */
public record GitLabUserLookup(
    String globalId,
    String username,
    @Nullable String name,
    @Nullable String avatarUrl,
    @Nullable String webUrl,
    @Nullable String publicEmail
) {
    /** Convenience factory for callers that do not resolve {@code publicEmail}. */
    public static GitLabUserLookup of(
        String globalId,
        String username,
        @Nullable String name,
        @Nullable String avatarUrl,
        @Nullable String webUrl
    ) {
        return new GitLabUserLookup(globalId, username, name, avatarUrl, webUrl, null);
    }
}
