package de.tum.in.www1.hephaestus.gitprovider.label.gitlab.dto;

import java.util.Map;
import org.springframework.lang.Nullable;

/**
 * DTO for GitLab labels from GraphQL sync responses.
 * <p>
 * Maps GitLab's {@code title} field to the application's {@code name} convention.
 * Used exclusively by the sync service; webhook-embedded labels are handled via
 * {@link de.tum.in.www1.hephaestus.gitprovider.common.gitlab.dto.GitLabWebhookLabel}.
 *
 * @param globalId    GitLab global ID (e.g., {@code "gid://gitlab/ProjectLabel/123"})
 * @param title       the label title (maps to {@code Label.name})
 * @param color       hex color code (e.g., {@code "#428BCA"})
 * @param description optional label description
 * @param createdAt   ISO-8601 timestamp from GraphQL
 * @param updatedAt   ISO-8601 timestamp from GraphQL
 */
public record GitLabLabelDTO(
    @Nullable String globalId,
    String title,
    @Nullable String color,
    @Nullable String description,
    @Nullable String createdAt,
    @Nullable String updatedAt
) {
    /**
     * Creates a DTO from a GraphQL response node map.
     *
     * @param node the GraphQL node fields
     * @return the DTO, or null if title is missing
     */
    @Nullable
    public static GitLabLabelDTO fromGraphQlNode(@Nullable Map<String, Object> node) {
        if (node == null) {
            return null;
        }
        String title = (String) node.get("title");
        if (title == null || title.isBlank()) {
            return null;
        }
        return new GitLabLabelDTO(
            (String) node.get("id"),
            title,
            (String) node.get("color"),
            (String) node.get("description"),
            node.get("createdAt") != null ? node.get("createdAt").toString() : null,
            node.get("updatedAt") != null ? node.get("updatedAt").toString() : null
        );
    }
}
