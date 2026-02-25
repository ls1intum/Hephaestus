package de.tum.in.www1.hephaestus.gitprovider.discussion.github.dto;

import static de.tum.in.www1.hephaestus.gitprovider.common.DateTimeUtils.toInstant;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.GHDiscussionCategory;
import java.time.Instant;
import org.springframework.lang.Nullable;

/**
 * DTO for GitHub Discussion Categories.
 * <p>
 * Note: DiscussionCategory uses a String node ID as its primary key because
 * GitHub's GraphQL API doesn't expose databaseId for discussion categories.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GitHubDiscussionCategoryDTO(
    @JsonProperty("id") String nodeId,
    @JsonProperty("name") String name,
    @JsonProperty("slug") String slug,
    @JsonProperty("emoji") String emoji,
    @JsonProperty("description") String description,
    @JsonProperty("is_answerable") boolean isAnswerable,
    @JsonProperty("created_at") Instant createdAt,
    @JsonProperty("updated_at") Instant updatedAt
) {
    // ========== STATIC FACTORY METHODS FOR GRAPHQL RESPONSES ==========

    /**
     * Creates a GitHubDiscussionCategoryDTO from a GraphQL DiscussionCategory model.
     *
     * @param category the GraphQL DiscussionCategory (may be null)
     * @return GitHubDiscussionCategoryDTO or null if category is null
     */
    @Nullable
    public static GitHubDiscussionCategoryDTO fromDiscussionCategory(@Nullable GHDiscussionCategory category) {
        if (category == null) {
            return null;
        }
        return new GitHubDiscussionCategoryDTO(
            category.getId(),
            category.getName(),
            category.getSlug(),
            category.getEmoji(),
            category.getDescription(),
            category.getIsAnswerable(),
            toInstant(category.getCreatedAt()),
            toInstant(category.getUpdatedAt())
        );
    }
}
