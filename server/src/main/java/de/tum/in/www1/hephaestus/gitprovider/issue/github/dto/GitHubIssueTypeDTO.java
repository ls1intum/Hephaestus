package de.tum.in.www1.hephaestus.gitprovider.issue.github.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.GHIssueType;
import org.springframework.lang.Nullable;

/**
 * DTO for GitHub issue type.
 * <p>
 * Works with both webhook JSON and GraphQL responses.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GitHubIssueTypeDTO(
    @JsonProperty("id") Long id,
    @JsonProperty("node_id") String nodeId,
    @JsonProperty("name") String name,
    @JsonProperty("description") String description,
    @JsonProperty("color") String color,
    @JsonProperty("is_enabled") Boolean isEnabled
) {
    /**
     * Get the node ID, preferring nodeId for REST, id for GraphQL.
     */
    public String getNodeId() {
        // GraphQL returns 'id' as the node ID string
        // REST returns 'node_id' as the node ID string
        if (nodeId != null) {
            return nodeId;
        }
        // For GraphQL where id is already the node ID string
        return id != null ? String.valueOf(id) : null;
    }

    // ========== STATIC FACTORY METHODS FOR GRAPHQL RESPONSES ==========

    /**
     * Creates a GitHubIssueTypeDTO from a GraphQL GHIssueType model.
     */
    @Nullable
    public static GitHubIssueTypeDTO fromIssueType(@Nullable GHIssueType issueType) {
        if (issueType == null) {
            return null;
        }
        return new GitHubIssueTypeDTO(
            null,
            issueType.getId(),
            issueType.getName(),
            issueType.getDescription(),
            issueType.getColor() != null ? issueType.getColor().name().toLowerCase() : null,
            issueType.getIsEnabled()
        );
    }
}
