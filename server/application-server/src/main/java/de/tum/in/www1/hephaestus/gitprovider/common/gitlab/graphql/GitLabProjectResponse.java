package de.tum.in.www1.hephaestus.gitprovider.common.gitlab.graphql;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.lang.Nullable;

/**
 * Response POJO for GitLab Project GraphQL queries.
 * <p>
 * Maps to project nodes returned by the {@code GetGroupProjects} and
 * {@code GetProject} queries. The {@code group} field is only populated
 * when fetching a single project via {@code GetProject}.
 *
 * @param id             GitLab Global ID (e.g., {@code gid://gitlab/Project/123})
 * @param fullPath       fully qualified project path (e.g., {@code org/my-project})
 * @param name           project display name
 * @param webUrl         web URL for the project (nullable per schema)
 * @param description    project description (nullable)
 * @param visibility     visibility level: {@code public}, {@code internal}, or {@code private} (nullable per schema)
 * @param archived       whether the project is archived (nullable per schema, defaults to false)
 * @param createdAt      ISO-8601 creation timestamp (nullable)
 * @param lastActivityAt ISO-8601 last activity timestamp (nullable)
 * @param group          parent group (nullable, only from GetProject query)
 * @param repository     repository metadata (nullable for empty repos)
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GitLabProjectResponse(
    String id,
    String fullPath,
    String name,
    @Nullable String webUrl,
    @Nullable String description,
    @Nullable String visibility,
    @Nullable Boolean archived,
    @Nullable String createdAt,
    @Nullable String lastActivityAt,
    @Nullable GitLabGroupResponse group,
    @Nullable RepositoryInfo repository
) {
    /**
     * Repository metadata from the GraphQL response.
     *
     * @param rootRef the default branch name (nullable for empty repos)
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RepositoryInfo(@Nullable String rootRef) {}
}
