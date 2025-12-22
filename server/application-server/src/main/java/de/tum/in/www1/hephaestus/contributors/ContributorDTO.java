package de.tum.in.www1.hephaestus.contributors;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.lang.NonNull;

/**
 * Data transfer object representing a GitHub contributor.
 * Used to display contributor information on the public about page.
 */
public record ContributorDTO(
    @NonNull Long id,
    @NonNull String login,
    @NonNull String name,
    @NonNull String avatarUrl,
    @NonNull String htmlUrl,
    int contributions
) {
    /**
     * DTO for parsing GitHub API contributor response.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record GitHubContributorResponse(
        @JsonProperty("id") Long id,
        @JsonProperty("login") String login,
        @JsonProperty("avatar_url") String avatarUrl,
        @JsonProperty("html_url") String htmlUrl,
        @JsonProperty("contributions") int contributions
    ) {
        public ContributorDTO toContributorDTO() {
            return new ContributorDTO(
                id,
                login,
                login, // Use login as name (full name requires separate API call)
                avatarUrl,
                htmlUrl,
                contributions
            );
        }
    }
}
