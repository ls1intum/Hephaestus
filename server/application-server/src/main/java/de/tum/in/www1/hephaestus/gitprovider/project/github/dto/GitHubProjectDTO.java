package de.tum.in.www1.hephaestus.gitprovider.project.github.dto;

import static de.tum.in.www1.hephaestus.gitprovider.common.DateTimeUtils.toInstant;
import static de.tum.in.www1.hephaestus.gitprovider.common.DateTimeUtils.uriToString;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.GHProjectV2;
import de.tum.in.www1.hephaestus.gitprovider.user.github.dto.GitHubUserDTO;
import java.math.BigInteger;
import java.time.Instant;
import org.springframework.lang.Nullable;

/**
 * Domain DTO for GitHub Projects V2.
 * <p>
 * This is the unified model used by both GraphQL sync and webhook handlers.
 * It can be constructed from any source (GraphQL, REST, webhook payload).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GitHubProjectDTO(
    @JsonProperty("id") Long id,
    @JsonProperty("database_id") Long databaseId,
    @JsonProperty("node_id") String nodeId,
    @JsonProperty("number") int number,
    @JsonProperty("title") String title,
    @JsonProperty("short_description") String shortDescription,
    @JsonProperty("readme") String readme,
    @JsonProperty("template") boolean template,
    @JsonProperty("url") String url,
    @JsonProperty("closed") boolean closed,
    @JsonProperty("closed_at") Instant closedAt,
    @JsonProperty("public") boolean isPublic,
    @JsonProperty("creator") GitHubUserDTO creator,
    @JsonProperty("created_at") Instant createdAt,
    @JsonProperty("updated_at") Instant updatedAt
) {
    /**
     * Get the database ID, preferring databaseId over id for GraphQL responses.
     */
    public Long getDatabaseId() {
        return databaseId != null ? databaseId : id;
    }

    /**
     * Check if the project is closed.
     * <p>
     * For webhook payloads, GitHub may not include the `closed` field directly,
     * so we also check if `closed_at` is set.
     *
     * @return true if the project is closed (either `closed` is true or `closed_at` is non-null)
     */
    public boolean isClosed() {
        return closed || closedAt != null;
    }

    // ========== STATIC FACTORY METHODS FOR GRAPHQL RESPONSES ==========

    /**
     * Creates a GitHubProjectDTO from a GraphQL GHProjectV2 model.
     *
     * @param project the GraphQL GHProjectV2 (may be null)
     * @return GitHubProjectDTO or null if project is null
     */
    @Nullable
    public static GitHubProjectDTO fromProjectV2(@Nullable GHProjectV2 project) {
        if (project == null) {
            return null;
        }

        return new GitHubProjectDTO(
            null,
            toLong(project.getFullDatabaseId()),
            project.getId(),
            project.getNumber(),
            project.getTitle(),
            project.getShortDescription(),
            project.getReadme(),
            project.getTemplate(),
            uriToString(project.getUrl()),
            project.getClosed(),
            toInstant(project.getClosedAt()),
            project.getPublic(),
            GitHubUserDTO.fromActor(project.getCreator()),
            toInstant(project.getCreatedAt()),
            toInstant(project.getUpdatedAt())
        );
    }

    // ========== CONVERSION HELPERS ==========

    @Nullable
    private static Long toLong(@Nullable BigInteger value) {
        if (value == null) {
            return null;
        }
        try {
            return value.longValueExact();
        } catch (ArithmeticException e) {
            // BigInteger value exceeds Long.MAX_VALUE - use longValue() which truncates
            // This should be extremely rare as GitHub database IDs are sequential
            return value.longValue();
        }
    }
}
