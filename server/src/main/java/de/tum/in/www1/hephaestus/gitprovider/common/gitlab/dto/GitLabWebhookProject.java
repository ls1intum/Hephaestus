package de.tum.in.www1.hephaestus.gitprovider.common.gitlab.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Shared DTO for GitLab webhook project objects.
 * <p>
 * Embedded in issue, merge request, and note webhook events.
 *
 * @param id                the GitLab project database ID
 * @param name              the project name
 * @param webUrl            the project's web URL
 * @param pathWithNamespace the full path (e.g., {@code group/project})
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GitLabWebhookProject(
    Long id,
    String name,
    @JsonProperty("web_url") String webUrl,
    @JsonProperty("path_with_namespace") String pathWithNamespace
) {}
