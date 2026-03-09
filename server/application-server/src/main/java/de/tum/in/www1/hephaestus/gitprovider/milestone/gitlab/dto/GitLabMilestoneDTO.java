package de.tum.in.www1.hephaestus.gitprovider.milestone.gitlab.dto;

import de.tum.in.www1.hephaestus.gitprovider.common.gitlab.GitLabSyncConstants;
import java.util.Map;
import org.springframework.lang.Nullable;

/**
 * DTO for GitLab milestones used by both sync and webhook paths.
 * <p>
 * Normalizes data from GraphQL and webhook sources into a common format
 * for {@link de.tum.in.www1.hephaestus.gitprovider.milestone.gitlab.GitLabMilestoneProcessor}.
 *
 * @param nativeId           the GitLab numeric database ID
 * @param iid                project-scoped sequential number (maps to {@code Milestone.number})
 * @param title              milestone title
 * @param description        optional description
 * @param state              raw state string ({@code "active"} or {@code "closed"})
 * @param dueDate            date-only string (e.g., {@code "2026-06-01"})
 * @param webPath            relative URL path (GraphQL only)
 * @param projectWebUrl      full project web URL (webhook only, for htmlUrl construction)
 * @param groupMilestone     true if inherited from a group
 * @param closedIssuesCount  from GraphQL stats
 * @param totalIssuesCount   from GraphQL stats
 * @param createdAt          ISO-8601 or webhook timestamp
 * @param updatedAt          ISO-8601 or webhook timestamp
 */
public record GitLabMilestoneDTO(
    long nativeId,
    int iid,
    String title,
    @Nullable String description,
    String state,
    @Nullable String dueDate,
    @Nullable String webPath,
    @Nullable String projectWebUrl,
    boolean groupMilestone,
    @Nullable Integer closedIssuesCount,
    @Nullable Integer totalIssuesCount,
    @Nullable String createdAt,
    @Nullable String updatedAt
) {
    /**
     * Creates a DTO from a GraphQL response node map.
     *
     * @param node the GraphQL node fields
     * @return the DTO, or null if required fields are missing
     */
    @Nullable
    @SuppressWarnings("unchecked")
    public static GitLabMilestoneDTO fromGraphQlNode(@Nullable Map<String, Object> node) {
        if (node == null) {
            return null;
        }

        String globalId = (String) node.get("id");
        String title = (String) node.get("title");
        if (globalId == null || title == null) {
            return null;
        }

        long nativeId;
        try {
            nativeId = GitLabSyncConstants.extractNumericId(globalId);
        } catch (IllegalArgumentException e) {
            return null;
        }

        // iid comes as a String from GraphQL (GitLab ID scalar)
        int iid;
        Object iidObj = node.get("iid");
        if (iidObj instanceof Number n) {
            iid = n.intValue();
        } else if (iidObj instanceof String s) {
            try {
                iid = Integer.parseInt(s);
            } catch (NumberFormatException e) {
                return null;
            }
        } else {
            return null;
        }

        String state = (String) node.get("state");
        if (state == null) {
            state = "active";
        }

        boolean groupMilestone = Boolean.TRUE.equals(node.get("groupMilestone"));

        Integer closedIssuesCount = null;
        Integer totalIssuesCount = null;
        Object statsObj = node.get("stats");
        if (statsObj instanceof Map<?, ?> statsMap) {
            Map<String, Object> stats = (Map<String, Object>) statsMap;
            closedIssuesCount = stats.get("closedIssuesCount") instanceof Number n ? n.intValue() : null;
            totalIssuesCount = stats.get("totalIssuesCount") instanceof Number n ? n.intValue() : null;
        }

        return new GitLabMilestoneDTO(
            nativeId,
            iid,
            title,
            (String) node.get("description"),
            state,
            node.get("dueDate") != null ? node.get("dueDate").toString() : null,
            (String) node.get("webPath"),
            null, // projectWebUrl not available from GraphQL
            groupMilestone,
            closedIssuesCount,
            totalIssuesCount,
            node.get("createdAt") != null ? node.get("createdAt").toString() : null,
            node.get("updatedAt") != null ? node.get("updatedAt").toString() : null
        );
    }

    /**
     * Creates a DTO from a webhook event.
     *
     * @param event the milestone webhook event
     * @return the DTO, or null if required fields are missing
     */
    @Nullable
    public static GitLabMilestoneDTO fromWebhookEvent(@Nullable GitLabMilestoneEventDTO event) {
        if (event == null || event.objectAttributes() == null) {
            return null;
        }

        var attrs = event.objectAttributes();
        if (attrs.title() == null) {
            return null;
        }

        // Webhook milestones are always project-level (groupId is null)
        boolean isGroupMilestone = attrs.groupId() != null;

        String projectWebUrl = event.project() != null ? event.project().webUrl() : null;

        return new GitLabMilestoneDTO(
            attrs.id(),
            attrs.iid(),
            attrs.title(),
            attrs.description(),
            attrs.state() != null ? attrs.state() : "active",
            attrs.dueDate(),
            null, // webPath not available from webhook
            projectWebUrl,
            isGroupMilestone,
            null, // counts not available from webhook
            null,
            attrs.createdAt(),
            attrs.updatedAt()
        );
    }
}
