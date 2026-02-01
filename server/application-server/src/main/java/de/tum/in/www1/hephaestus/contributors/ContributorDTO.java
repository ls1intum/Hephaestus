package de.tum.in.www1.hephaestus.contributors;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.lang.NonNull;

/**
 * Data transfer object representing a GitHub contributor.
 * Used to display contributor information on the public about page.
 */
@Schema(description = "Information about a contributor to the Hephaestus project")
public record ContributorDTO(
    @NonNull @Schema(description = "GitHub user ID") Long id,
    @NonNull @Schema(description = "GitHub username") String login,
    @NonNull @Schema(description = "Display name of the contributor") String name,
    @NonNull @Schema(description = "URL to the contributor's avatar image") String avatarUrl,
    @NonNull @Schema(description = "URL to the contributor's GitHub profile") String htmlUrl,
    @Schema(description = "Number of contributions to the project", example = "42") int contributions
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
        /**
         * Converts to ContributorDTO with full name from user API.
         * Falls back to login if name is not available.
         */
        public ContributorDTO toContributorDTO(String fullName) {
            return new ContributorDTO(
                id,
                login,
                fullName != null && !fullName.isBlank() ? fullName : login,
                avatarUrl,
                htmlUrl,
                contributions
            );
        }
    }
}
