package de.tum.in.www1.hephaestus.gitprovider.common.gitlab.graphql;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.lang.Nullable;

/**
 * Response POJO for GitLab descendant group GraphQL queries.
 * <p>
 * Maps to nodes returned by {@code group.descendantGroups} in the
 * {@code GetGroupDescendants} query.
 *
 * @param id          GitLab Global ID (e.g., {@code gid://gitlab/Group/42})
 * @param fullPath    fully qualified group path (e.g., {@code ase/introcourse/group1})
 * @param name        display name (e.g., "group1")
 * @param description group description (nullable)
 * @param webUrl      web URL for the group
 * @param visibility  visibility level: public, internal, or private (nullable)
 * @param parent      parent group reference (nullable for top-level groups)
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GitLabDescendantGroupResponse(
    String id,
    String fullPath,
    String name,
    @Nullable String description,
    String webUrl,
    @Nullable String visibility,
    @Nullable ParentRef parent
) {
    /**
     * Reference to the parent group.
     *
     * @param id       GitLab Global ID of the parent group
     * @param fullPath fully qualified path of the parent group
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ParentRef(String id, String fullPath) {}
}
