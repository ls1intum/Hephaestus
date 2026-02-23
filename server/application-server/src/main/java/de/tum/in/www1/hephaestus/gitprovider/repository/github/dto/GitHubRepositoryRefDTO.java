package de.tum.in.www1.hephaestus.gitprovider.repository.github.dto;

import static de.tum.in.www1.hephaestus.gitprovider.common.DateTimeUtils.uriToString;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.GHRepository;
import org.springframework.lang.Nullable;

/**
 * Lightweight DTO for repository references in webhook payloads and GraphQL responses.
 * <p>
 * Used to identify the repository context without loading the full entity.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GitHubRepositoryRefDTO(
    @JsonProperty("id") Long id,
    @JsonProperty("node_id") String nodeId,
    @JsonProperty("name") String name,
    @JsonProperty("full_name") String fullName,
    @JsonProperty("private") boolean isPrivate,
    @JsonProperty("html_url") String htmlUrl,
    @JsonProperty("default_branch") String defaultBranch
) {
    /**
     * Creates a GitHubRepositoryRefDTO from a GraphQL GHRepository model.
     * <p>
     * Used when creating stub issues for blockers/parents that reference
     * issues in different repositories.
     *
     * @param repository the GraphQL GHRepository (may be null)
     * @return GitHubRepositoryRefDTO or null if repository is null
     */
    @Nullable
    public static GitHubRepositoryRefDTO fromRepository(@Nullable GHRepository repository) {
        if (repository == null) {
            return null;
        }

        return new GitHubRepositoryRefDTO(
            repository.getDatabaseId() != null ? repository.getDatabaseId().longValue() : null,
            repository.getId(),
            repository.getName(),
            repository.getNameWithOwner(),
            repository.getIsPrivate(),
            uriToString(repository.getUrl()),
            null // defaultBranch not available in GraphQL GHRepository
        );
    }
}
