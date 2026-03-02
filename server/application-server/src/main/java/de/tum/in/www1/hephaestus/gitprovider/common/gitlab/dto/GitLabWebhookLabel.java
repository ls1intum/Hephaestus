package de.tum.in.www1.hephaestus.gitprovider.common.gitlab.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Shared DTO for GitLab webhook label objects.
 * <p>
 * Embedded in issue and merge request webhook events within the {@code labels} array.
 *
 * @param id    the GitLab label database ID
 * @param title the label name/title
 * @param color the label color (hex string, e.g., {@code "#428BCA"})
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GitLabWebhookLabel(Long id, String title, String color) {}
