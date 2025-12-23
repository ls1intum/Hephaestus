package de.tum.in.www1.hephaestus.gitprovider.milestone.github.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.Milestone;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.MilestoneState;
import java.net.URI;
import java.time.Instant;
import java.time.OffsetDateTime;
import org.springframework.lang.Nullable;

/**
 * DTO for GitHub milestones.
 * <p>
 * Provides factory methods for creating from both REST (webhook) and GraphQL responses.
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
) {
    // ========== STATIC FACTORY METHODS FOR GRAPHQL RESPONSES ==========

    /**
     * Creates a GitHubMilestoneDTO from a GraphQL Milestone model.
     */
    @Nullable
    public static GitHubMilestoneDTO fromMilestone(@Nullable Milestone milestone) {
        if (milestone == null) {
            return null;
        }
        return new GitHubMilestoneDTO(
            null,
            milestone.getNumber(),
            milestone.getTitle(),
            milestone.getDescription(),
            convertState(milestone.getState()),
            toInstant(milestone.getDueOn()),
            uriToString(milestone.getUrl())
        );
    }

    private static String convertState(@Nullable MilestoneState state) {
        if (state == null) {
            return "open";
        }
        return state.name().toLowerCase();
    }

    @Nullable
    private static Instant toInstant(@Nullable OffsetDateTime dateTime) {
        return dateTime != null ? dateTime.toInstant() : null;
    }

    @Nullable
    private static String uriToString(@Nullable URI uri) {
        return uri != null ? uri.toString() : null;
    }
}
