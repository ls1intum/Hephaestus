package de.tum.in.www1.hephaestus.gitprovider.milestone.github.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

/**
 * DTO for GitHub milestones.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GitHubMilestoneDTO(
    @JsonProperty("id") Long id,
    @JsonProperty("number") int number,
    @JsonProperty("title") String title,
    @JsonProperty("description") String description,
    @JsonProperty("state") String state,
    @JsonProperty("due_on") Instant dueOn,
    @JsonProperty("html_url") String htmlUrl
) {}
