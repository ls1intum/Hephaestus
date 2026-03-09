package de.tum.in.www1.hephaestus.gitprovider.common.gitlab.graphql;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.lang.Nullable;

/**
 * Response POJO for GitLab Group Member GraphQL queries.
 * <p>
 * Maps to the {@code groupMembers.nodes} field returned by the {@code GetGroupMembers} query.
 *
 * @param user        the member user data
 * @param accessLevel the member's access level in the group
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GitLabGroupMemberResponse(@Nullable GitLabMemberUser user, @Nullable GitLabAccessLevel accessLevel) {
    /**
     * User data from a group membership node.
     *
     * @param id        GitLab Global ID (e.g., {@code gid://gitlab/User/42})
     * @param username  the user's login name
     * @param name      the user's display name (nullable)
     * @param avatarUrl the user's avatar URL (nullable)
     * @param webUrl    the user's profile URL (nullable)
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record GitLabMemberUser(
        String id,
        String username,
        @Nullable String name,
        @Nullable String avatarUrl,
        @Nullable String webUrl
    ) {}

    /**
     * Access level for a group member.
     * <p>
     * GitLab access levels: MINIMAL_ACCESS(5), GUEST(10), PLANNER(15), REPORTER(20),
     * DEVELOPER(30), MAINTAINER(40), OWNER(50).
     *
     * @param stringValue  human-readable access level (e.g., "DEVELOPER")
     * @param integerValue numeric access level (e.g., 30)
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record GitLabAccessLevel(@Nullable String stringValue, @Nullable Integer integerValue) {}
}
