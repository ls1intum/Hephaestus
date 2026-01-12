package de.tum.in.www1.hephaestus.gitprovider.milestone.github.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.GHMilestone;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.GHMilestoneState;
import static de.tum.in.www1.hephaestus.gitprovider.common.DateTimeUtils.toInstant;
import static de.tum.in.www1.hephaestus.gitprovider.common.DateTimeUtils.uriToString;

import java.time.Instant;
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
    @JsonProperty("html_url") String htmlUrl,
    @JsonProperty("open_issues") Integer openIssuesCount,
    @JsonProperty("closed_issues") Integer closedIssuesCount
) {
    // ========== STATIC FACTORY METHODS FOR GRAPHQL RESPONSES ==========

    /**
     * Creates a GitHubMilestoneDTO from a GraphQL GHMilestone model.
     */
    @Nullable
    public static GitHubMilestoneDTO fromMilestone(@Nullable GHMilestone milestone) {
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
            uriToString(milestone.getUrl()),
            milestone.getOpenIssueCount(),
            milestone.getClosedIssueCount()
        );
    }

    private static String convertState(@Nullable GHMilestoneState state) {
        if (state == null) {
            return "open";
        }
        return state.name().toLowerCase();
    }

}
