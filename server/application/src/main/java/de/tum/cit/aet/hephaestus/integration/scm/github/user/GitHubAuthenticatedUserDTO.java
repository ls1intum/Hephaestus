package de.tum.cit.aet.hephaestus.integration.scm.github.user;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.jspecify.annotations.Nullable;

/** GitHub REST user fields used to synchronize a PAT-backed connection owner. */
public record GitHubAuthenticatedUserDTO(
        @Nullable Long id,
        @Nullable String login,
        @Nullable String name,
        @JsonProperty("avatar_url") @Nullable String avatarUrl,
        @JsonProperty("html_url") @Nullable String htmlUrl) {}
