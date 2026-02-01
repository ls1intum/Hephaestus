package de.tum.in.www1.hephaestus.gitprovider.project.github.dto;

import static de.tum.in.www1.hephaestus.gitprovider.common.DateTimeUtils.toInstant;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.GHDraftIssue;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.GHIssue;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.GHProjectV2Item;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.GHProjectV2ItemContent;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.GHProjectV2ItemFieldValue;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.GHProjectV2ItemFieldValueConnection;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.GHProjectV2ItemType;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.GHPullRequest;
import de.tum.in.www1.hephaestus.gitprovider.project.ProjectItem;
import java.math.BigInteger;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.springframework.lang.Nullable;

/**
 * Domain DTO for GitHub Projects V2 items.
 * <p>
 * An item represents a row in a project and can contain:
 * - ISSUE: Links to an existing Issue
 * - PULL_REQUEST: Links to an existing Pull Request
 * - DRAFT_ISSUE: A draft that hasn't been converted to a real issue yet
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GitHubProjectItemDTO(
    @JsonProperty("id") Long id,
    @JsonProperty("database_id") Long databaseId,
    @JsonProperty("node_id") String nodeId,
    @JsonProperty("project_node_id") String projectNodeId,
    @JsonProperty("content_type") String contentType,
    @JsonProperty("issue_id") Long issueId,
    @JsonProperty("issue_number") Integer issueNumber,
    @JsonProperty("draft_title") String draftTitle,
    @JsonProperty("draft_body") String draftBody,
    @JsonProperty("archived") boolean archived,
    @JsonProperty("field_values") List<GitHubProjectFieldValueDTO> fieldValues,
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
     * Get the content type as an enum.
     */
    @Nullable
    public ProjectItem.ContentType getContentTypeEnum() {
        if (contentType == null) {
            return null;
        }
        return switch (contentType.toUpperCase()) {
            case "ISSUE" -> ProjectItem.ContentType.ISSUE;
            case "PULL_REQUEST", "PULLREQUEST" -> ProjectItem.ContentType.PULL_REQUEST;
            case "DRAFT_ISSUE", "DRAFTISSUE" -> ProjectItem.ContentType.DRAFT_ISSUE;
            default -> null;
        };
    }

    // ========== STATIC FACTORY METHODS FOR GRAPHQL RESPONSES ==========

    /**
     * Creates a GitHubProjectItemDTO from a GraphQL GHProjectV2Item model.
     *
     * @param item the GraphQL GHProjectV2Item (may be null)
     * @return GitHubProjectItemDTO or null if item is null
     */
    @Nullable
    public static GitHubProjectItemDTO fromProjectV2Item(@Nullable GHProjectV2Item item) {
        if (item == null) {
            return null;
        }

        String contentType = extractContentType(item.getType());
        Long issueId = null;
        Integer issueNumber = null;
        String draftTitle = null;
        String draftBody = null;

        GHProjectV2ItemContent content = item.getContent();
        if (content != null) {
            if (content instanceof GHIssue issue) {
                issueId = toLong(issue.getFullDatabaseId());
                issueNumber = issue.getNumber();
            } else if (content instanceof GHPullRequest pr) {
                issueId = toLong(pr.getFullDatabaseId());
                issueNumber = pr.getNumber();
            } else if (content instanceof GHDraftIssue draft) {
                draftTitle = draft.getTitle();
                draftBody = draft.getBody();
            }
        }

        // Extract field values from the GraphQL response
        List<GitHubProjectFieldValueDTO> fieldValues = extractFieldValues(item.getFieldValues());

        return new GitHubProjectItemDTO(
            null,
            toLong(item.getFullDatabaseId()),
            item.getId(),
            null, // projectNodeId not available from GraphQL item query
            contentType,
            issueId,
            issueNumber,
            draftTitle,
            draftBody,
            item.getIsArchived(),
            fieldValues,
            toInstant(item.getCreatedAt()),
            toInstant(item.getUpdatedAt())
        );
    }

    /**
     * Extracts field values from a GraphQL field value connection.
     *
     * @param fieldValuesConnection the GraphQL field values connection (may be null)
     * @return list of field value DTOs, or empty list if no values
     */
    private static List<GitHubProjectFieldValueDTO> extractFieldValues(
        @Nullable GHProjectV2ItemFieldValueConnection fieldValuesConnection
    ) {
        if (fieldValuesConnection == null || fieldValuesConnection.getNodes() == null) {
            return Collections.emptyList();
        }

        List<GitHubProjectFieldValueDTO> result = new ArrayList<>();
        for (GHProjectV2ItemFieldValue fieldValue : fieldValuesConnection.getNodes()) {
            GitHubProjectFieldValueDTO dto = GitHubProjectFieldValueDTO.fromFieldValue(fieldValue);
            if (dto != null && dto.fieldId() != null) {
                result.add(dto);
            }
        }
        return result;
    }

    // ========== CONVERSION HELPERS ==========

    @Nullable
    private static Long toLong(@Nullable BigInteger value) {
        if (value == null) {
            return null;
        }
        return value.longValueExact();
    }

    @Nullable
    private static String extractContentType(@Nullable GHProjectV2ItemType type) {
        if (type == null) {
            return null;
        }
        return type.name();
    }
}
