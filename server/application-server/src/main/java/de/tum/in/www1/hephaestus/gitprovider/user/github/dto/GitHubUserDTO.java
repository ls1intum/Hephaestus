package de.tum.in.www1.hephaestus.gitprovider.user.github.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * DTO for GitHub user references.
 * <p>
 * Used across all entities: issues, pull requests, comments, reviews, etc.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GitHubUserDTO(
    @JsonProperty("id") Long id,
    @JsonProperty("database_id") Long databaseId,
    @JsonProperty("login") String login,
    @JsonProperty("avatar_url") String avatarUrl,
    @JsonProperty("html_url") String htmlUrl,
    @JsonProperty("name") String name,
    @JsonProperty("email") String email
) {
    /**
     * Get the database ID, preferring databaseId over id for GraphQL responses.
     */
    public Long getDatabaseId() {
        return databaseId != null ? databaseId : id;
    }
}
