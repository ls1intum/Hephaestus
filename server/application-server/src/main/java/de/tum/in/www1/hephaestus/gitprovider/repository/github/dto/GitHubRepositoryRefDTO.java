package de.tum.in.www1.hephaestus.gitprovider.repository.github.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Lightweight DTO for repository references in webhook payloads.
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
    @JsonProperty("html_url") String htmlUrl
) {}
