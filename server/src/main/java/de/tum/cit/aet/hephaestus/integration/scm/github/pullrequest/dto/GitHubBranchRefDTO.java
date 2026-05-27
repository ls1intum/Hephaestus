package de.tum.cit.aet.hephaestus.integration.scm.github.pullrequest.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * DTO representing a branch reference in a pull request (head or base).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GitHubBranchRefDTO(
    @JsonProperty("ref") String ref,
    @JsonProperty("sha") String sha,
    @JsonProperty("label") String label
) {}
