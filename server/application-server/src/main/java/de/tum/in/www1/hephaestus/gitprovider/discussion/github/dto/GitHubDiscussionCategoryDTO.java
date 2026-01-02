package de.tum.in.www1.hephaestus.gitprovider.discussion.github.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.DiscussionCategory;
import java.time.Instant;
import java.time.OffsetDateTime;
import org.springframework.lang.Nullable;

/**
 * DTO for GitHub discussion categories.
 * <p>
 * Provides factory methods for creating from both REST (webhook) and GraphQL responses.
 * <p>
 * Note: GitHub's GraphQL API does not expose databaseId for DiscussionCategory,
 * so we use the node ID (id field) as the primary identifier. The {@code id} field
 * in this DTO exists only for webhook payload compatibility where GitHub sends
 * a numeric ID.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GitHubDiscussionCategoryDTO(
    @JsonProperty("id") Long id,
    @JsonProperty("node_id") String nodeId,
    @JsonProperty("name") String name,
    @JsonProperty("slug") String slug,
    @JsonProperty("emoji") String emoji,
    @JsonProperty("description") String description,
    @JsonProperty("is_answerable") Boolean isAnswerable,
    @JsonProperty("created_at") Instant createdAt
) {
    // ========== STATIC FACTORY METHODS FOR GRAPHQL RESPONSES ==========

    /**
     * Creates a GitHubDiscussionCategoryDTO from a GraphQL DiscussionCategory model.
     * <p>
     * Note: Since GraphQL doesn't provide databaseId for categories, we use the
     * node ID as the primary identifier (stored in nodeId field).
     */
    @Nullable
    public static GitHubDiscussionCategoryDTO fromDiscussionCategory(@Nullable DiscussionCategory category) {
        if (category == null) {
            return null;
        }
        return new GitHubDiscussionCategoryDTO(
            null, // GraphQL doesn't expose databaseId for categories
            category.getId(), // This is the node_id (e.g., "DIC_kwDOBk...")
            category.getName(),
            category.getSlug(),
            category.getEmoji(),
            category.getDescription(),
            category.getIsAnswerable(),
            toInstant(category.getCreatedAt())
        );
    }

    @Nullable
    private static Instant toInstant(@Nullable OffsetDateTime dateTime) {
        return dateTime != null ? dateTime.toInstant() : null;
    }
}
