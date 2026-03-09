package de.tum.in.www1.hephaestus.gitprovider.common.gitlab.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Shared DTO for GitLab webhook user objects.
 * <p>
 * GitLab webhooks embed user data in various event types (issues, merge requests, notes).
 * This record captures the common user fields shared across all event types.
 *
 * @param id        the GitLab user database ID
 * @param username  the user's login name
 * @param name      the user's display name
 * @param avatarUrl the user's avatar URL
 * @param email     the user's email (may be null depending on privacy settings)
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GitLabWebhookUser(
    Long id,
    String username,
    String name,
    @JsonProperty("avatar_url") String avatarUrl,
    String email
) {}
