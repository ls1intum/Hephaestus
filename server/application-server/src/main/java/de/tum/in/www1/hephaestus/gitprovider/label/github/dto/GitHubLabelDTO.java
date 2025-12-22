package de.tum.in.www1.hephaestus.gitprovider.label.github.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * DTO for GitHub labels.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GitHubLabelDTO(
    @JsonProperty("id") Long id,
    @JsonProperty("node_id") String nodeId,
    @JsonProperty("name") String name,
    @JsonProperty("description") String description,
    @JsonProperty("color") String color
) {}
