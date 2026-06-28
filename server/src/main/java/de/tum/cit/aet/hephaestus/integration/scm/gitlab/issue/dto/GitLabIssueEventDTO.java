package de.tum.cit.aet.hephaestus.integration.scm.gitlab.issue.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import de.tum.cit.aet.hephaestus.integration.scm.gitlab.common.GitLabEventAction;
import de.tum.cit.aet.hephaestus.integration.scm.gitlab.common.dto.GitLabWebhookLabel;
import de.tum.cit.aet.hephaestus.integration.scm.gitlab.common.dto.GitLabWebhookProject;
import de.tum.cit.aet.hephaestus.integration.scm.gitlab.common.dto.GitLabWebhookUser;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;

/**
 * DTO for GitLab issue webhook events.
 * <p>
 * Maps both {@code event_type: "issue"} and {@code event_type: "confidential_issue"} payloads.
 * Both arrive on the same NATS subject ({@code object_kind: "issue"}).
 *
 * @param objectKind       always "issue"
 * @param eventType        "issue" or "confidential_issue"
 * @param user             the user who triggered the event
 * @param project          the project context
 * @param objectAttributes the issue details
 * @param labels           current labels on the issue
 * @param assignees        current assignees
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GitLabIssueEventDTO(
    @JsonProperty("object_kind") String objectKind,
    @JsonProperty("event_type") String eventType,
    GitLabWebhookUser user,
    GitLabWebhookProject project,
    @JsonProperty("object_attributes") ObjectAttributes objectAttributes,
    @Nullable List<GitLabWebhookLabel> labels,
    @Nullable List<GitLabWebhookUser> assignees,
    @JsonProperty("changes") @Nullable Changes changes
) {
    /**
     * The {@code changes} diff GitLab sends on an {@code action=update} event. We only care about the
     * label delta — GitLab has no dedicated "labeled" action, so label changes arrive here.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Changes(@Nullable LabelsChange labels) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record LabelsChange(
        @Nullable List<GitLabWebhookLabel> previous,
        @Nullable List<GitLabWebhookLabel> current
    ) {}

    /**
     * Labels newly added in this update (current minus previous, keyed by id). Empty when the update
     * carried no label change — so an ordinary title/description edit never spuriously triggers
     * label-based detection.
     */
    public List<GitLabWebhookLabel> addedLabels() {
        if (changes == null || changes.labels() == null || changes.labels().current() == null) {
            return List.of();
        }
        List<GitLabWebhookLabel> previous = changes.labels().previous();
        Set<Long> previousIds =
            previous == null
                ? Set.of()
                : previous.stream().map(GitLabWebhookLabel::id).filter(Objects::nonNull).collect(Collectors.toSet());
        return changes
            .labels()
            .current()
            .stream()
            // A current label with a null id is treated as added: GitLab's changes.labels diff reliably carries
            // ids, so this branch is defensive only and deliberately favours over-firing IssueLabeled (better to
            // re-trigger detection than silently miss a real add) over under-firing on a malformed payload.
            .filter(label -> label.id() == null || !previousIds.contains(label.id()))
            .toList();
    }

    /**
     * The issue details within the webhook payload.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ObjectAttributes(
        Long id,
        Integer iid,
        String title,
        String description,
        String state,
        String action,
        boolean confidential,
        @JsonProperty("author_id") Long authorId,
        @JsonProperty("assignee_id") @Nullable Long assigneeId,
        @JsonProperty("milestone_id") @Nullable Long milestoneId,
        @JsonProperty("created_at") String createdAt,
        @JsonProperty("updated_at") String updatedAt,
        @JsonProperty("closed_at") @Nullable String closedAt,
        String url
    ) {}

    /**
     * Returns true if this is a confidential issue event.
     */
    public boolean isConfidential() {
        return objectAttributes != null && objectAttributes.confidential();
    }

    /**
     * Parses the action string to a GitLabEventAction enum.
     */
    public GitLabEventAction actionType() {
        if (objectAttributes == null || objectAttributes.action() == null) {
            return GitLabEventAction.UNKNOWN;
        }
        return GitLabEventAction.fromString(objectAttributes.action());
    }
}
