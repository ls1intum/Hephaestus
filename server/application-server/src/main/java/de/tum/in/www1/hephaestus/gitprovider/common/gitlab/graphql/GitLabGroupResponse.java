package de.tum.in.www1.hephaestus.gitprovider.common.gitlab.graphql;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.lang.Nullable;

/**
 * Response POJO for GitLab Group GraphQL queries.
 * <p>
 * Maps to the {@code group} field returned by the {@code GetGroup} and
 * {@code GetProject} queries. Uses a simple record instead of generated
 * code because GitLab's schema is large and codegen is not configured.
 *
 * @param id          GitLab Global ID (e.g., {@code gid://gitlab/Group/42})
 * @param fullPath    fully qualified group path (e.g., {@code org/team})
 * @param name        display name (non-null per schema; defensive null-check in processor)
 * @param avatarUrl   group avatar URL (nullable)
 * @param webUrl      web URL for the group (non-null per schema)
 * @param description group description (nullable)
 * @param visibility  visibility level: {@code public}, {@code internal}, or {@code private} (nullable)
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GitLabGroupResponse(
    String id,
    String fullPath,
    String name,
    @Nullable String avatarUrl,
    String webUrl,
    @Nullable String description,
    @Nullable String visibility
) {}
