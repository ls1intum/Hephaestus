package de.tum.in.www1.hephaestus.gitprovider.common.gitlab.graphql;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.lang.Nullable;

/**
 * Response POJO for GitLab group member GraphQL queries.
 * <p>
 * Maps to nodes returned by {@code group.groupMembers} in the
 * {@code GetGroupMembers} query.
 *
 * @param accessLevel the member's access level
 * @param user        the member's user information
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GitLabGroupMemberResponse(@Nullable AccessLevelRef accessLevel, @Nullable UserRef user) {
    /**
     * Access level information.
     *
     * @param stringValue the access level enum value (e.g., DEVELOPER, MAINTAINER, OWNER)
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AccessLevelRef(@Nullable String stringValue) {}

    /**
     * User information from the group membership.
     *
     * @param id        GitLab Global ID (e.g., {@code gid://gitlab/User/42})
     * @param username  the user's login handle
     * @param name      the user's display name (nullable)
     * @param avatarUrl the user's avatar URL (nullable)
     * @param webUrl    the user's profile URL (nullable)
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record UserRef(
        String id,
        String username,
        @Nullable String name,
        @Nullable String avatarUrl,
        @Nullable String webUrl
    ) {}
}
