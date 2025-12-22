package de.tum.in.www1.hephaestus.gitprovider.pullrequest.github.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubWebhookEvent;
import de.tum.in.www1.hephaestus.gitprovider.label.github.dto.GitHubLabelDTO;
import de.tum.in.www1.hephaestus.gitprovider.repository.github.dto.GitHubRepositoryRefDTO;
import de.tum.in.www1.hephaestus.gitprovider.user.github.dto.GitHubUserDTO;
import java.util.Map;

/**
 * DTO for GitHub pull request webhook event payloads.
 * <p>
 * Implements {@link GitHubWebhookEvent} for unified handling in message
 * handlers.
 * Common action checks (isOpened, isClosed, etc.) are inherited from the
 * interface.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GitHubPullRequestEventDTO(
    @JsonProperty("action") String action,
    @JsonProperty("number") int number,
    @JsonProperty("pull_request") GitHubPullRequestDTO pullRequest,
    @JsonProperty("repository") GitHubRepositoryRefDTO repository,
    @JsonProperty("sender") GitHubUserDTO sender,
    @JsonProperty("label") GitHubLabelDTO label,
    @JsonProperty("requested_reviewer") GitHubUserDTO requestedReviewer,
    @JsonProperty("changes") Map<String, Object> changes
)
    implements GitHubWebhookEvent {
    // PR-specific actions (not in base interface)

    /** Review requested from a user */
    public boolean isReviewRequested() {
        return "review_requested".equals(action);
    }

    /** Review request removed */
    public boolean isReviewRequestRemoved() {
        return "review_request_removed".equals(action);
    }

    /** New commits pushed */
    public boolean isSynchronize() {
        return "synchronize".equals(action);
    }

    /** PR converted from draft to ready */
    public boolean isReadyForReview() {
        return "ready_for_review".equals(action);
    }

    /** PR converted to draft */
    public boolean isConvertedToDraft() {
        return "converted_to_draft".equals(action);
    }

    /** PR locked */
    public boolean isLocked() {
        return "locked".equals(action);
    }

    /** PR unlocked */
    public boolean isUnlocked() {
        return "unlocked".equals(action);
    }

    /** Auto-merge enabled */
    public boolean isAutoMergeEnabled() {
        return "auto_merge_enabled".equals(action);
    }

    /** Auto-merge disabled */
    public boolean isAutoMergeDisabled() {
        return "auto_merge_disabled".equals(action);
    }

    /** PR added to merge queue */
    public boolean isEnqueued() {
        return "enqueued".equals(action);
    }

    /** PR removed from merge queue */
    public boolean isDequeued() {
        return "dequeued".equals(action);
    }
}
