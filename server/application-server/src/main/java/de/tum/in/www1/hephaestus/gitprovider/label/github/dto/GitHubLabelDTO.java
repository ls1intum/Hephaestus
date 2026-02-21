package de.tum.in.www1.hephaestus.gitprovider.label.github.dto;

import static de.tum.in.www1.hephaestus.gitprovider.common.DateTimeUtils.toInstant;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GraphQlConnectionOverflowDetector;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.GHLabel;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.GHLabelConnection;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import org.springframework.lang.Nullable;

/**
 * DTO for GitHub labels.
 * <p>
 * Provides factory methods for creating from both REST (webhook) and GraphQL responses.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GitHubLabelDTO(
    @JsonProperty("id") Long id,
    @JsonProperty("node_id") String nodeId,
    @JsonProperty("name") String name,
    @JsonProperty("description") String description,
    @JsonProperty("color") String color,
    @Nullable Instant createdAt,
    @Nullable Instant updatedAt
) {
    // ========== STATIC FACTORY METHODS FOR GRAPHQL RESPONSES ==========

    /**
     * Creates a GitHubLabelDTO from a GraphQL GHLabel model.
     */
    @Nullable
    public static GitHubLabelDTO fromLabel(@Nullable GHLabel label) {
        if (label == null) {
            return null;
        }
        return new GitHubLabelDTO(
            null,
            label.getId(),
            label.getName(),
            label.getDescription(),
            label.getColor(),
            toInstant(label.getCreatedAt()),
            toInstant(label.getUpdatedAt())
        );
    }

    /**
     * Creates a list of GitHubLabelDTOs from a GraphQL GHLabelConnection.
     *
     * @param connection the GraphQL label connection (may be null)
     * @param context    contextual description for overflow logging (e.g. "PR #42")
     * @return list of label DTOs, or empty list if connection is null
     */
    public static List<GitHubLabelDTO> fromLabelConnection(@Nullable GHLabelConnection connection, String context) {
        if (connection == null || connection.getNodes() == null) {
            return Collections.emptyList();
        }
        List<GitHubLabelDTO> result = connection
            .getNodes()
            .stream()
            .map(GitHubLabelDTO::fromLabel)
            .filter(dto -> dto != null)
            .toList();
        GraphQlConnectionOverflowDetector.check("labels", result.size(), connection.getTotalCount(), context);
        return result;
    }
}
