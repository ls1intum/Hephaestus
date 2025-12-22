package de.tum.in.www1.hephaestus.gitprovider.issue.github.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubWebhookEvent;
import de.tum.in.www1.hephaestus.gitprovider.label.github.dto.GitHubLabelDTO;
import de.tum.in.www1.hephaestus.gitprovider.repository.github.dto.GitHubRepositoryRefDTO;
import de.tum.in.www1.hephaestus.gitprovider.user.github.dto.GitHubUserDTO;
import java.util.Map;

/**
 * DTO for GitHub issue webhook event payloads.
 * <p>
 * Implements {@link GitHubWebhookEvent} for unified handling in message
 * handlers.
 * Common action checks (isOpened, isClosed, etc.) are inherited from the
 * interface.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GitHubIssueEventDTO(
    @JsonProperty("action") String action,
    @JsonProperty("issue") GitHubIssueDTO issue,
    @JsonProperty("repository") GitHubRepositoryRefDTO repository,
    @JsonProperty("sender") GitHubUserDTO sender,
    @JsonProperty("label") GitHubLabelDTO label,
    @JsonProperty("type") GitHubIssueTypeDTO issueType,
    @JsonProperty("changes") Map<String, Object> changes
)
    implements GitHubWebhookEvent {
    // Issue-specific actions (not in base interface)

    /** Issue type assigned (GitHub issue types feature) */
    public boolean isTyped() {
        return "typed".equals(action);
    }

    /** Issue type removed (GitHub issue types feature) */
    public boolean isUntyped() {
        return "untyped".equals(action);
    }

    /** Issue transferred to another repository */
    public boolean isTransferred() {
        return "transferred".equals(action);
    }

    /** Issue added to a milestone */
    public boolean isMilestoned() {
        return "milestoned".equals(action);
    }

    /** Issue removed from a milestone */
    public boolean isDemilestoned() {
        return "demilestoned".equals(action);
    }

    /** Issue pinned to the repository */
    public boolean isPinned() {
        return "pinned".equals(action);
    }

    /** Issue unpinned from the repository */
    public boolean isUnpinned() {
        return "unpinned".equals(action);
    }

    /** Issue locked (comments disabled) */
    public boolean isLocked() {
        return "locked".equals(action);
    }

    /** Issue unlocked (comments enabled) */
    public boolean isUnlocked() {
        return "unlocked".equals(action);
    }
}
