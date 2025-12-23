package de.tum.in.www1.hephaestus.gitprovider.common.github;

import de.tum.in.www1.hephaestus.gitprovider.repository.github.dto.GitHubRepositoryRefDTO;
import org.springframework.lang.Nullable;

/**
 * Common interface for all GitHub webhook event DTOs.
 * <p>
 * All webhook event DTOs (GitHubIssueEventDTO, GitHubPullRequestEventDTO,
 * GitHubCommentEventDTO, etc.) should implement this interface.
 * <p>
 * <b>Benefits:</b>
 * <ul>
 * <li>Unified handling in base message handler</li>
 * <li>Common repository/context extraction</li>
 * <li>Enables generic event processing pipelines</li>
 * </ul>
 */
public interface GitHubWebhookEvent {
    /**
     * The webhook action (e.g., "opened", "closed", "labeled").
     */
    String action();

    /**
     * The repository where this event occurred.
     * May be null for installation/organization-level events.
     */
    @Nullable
    GitHubRepositoryRefDTO repository();

    // ==================== Action Helper Methods ====================
    // Default implementations for common action checks

    default boolean isOpened() {
        return "opened".equals(action());
    }

    default boolean isEdited() {
        return "edited".equals(action());
    }

    default boolean isClosed() {
        return "closed".equals(action());
    }

    default boolean isReopened() {
        return "reopened".equals(action());
    }

    default boolean isDeleted() {
        return "deleted".equals(action());
    }

    default boolean isLabeled() {
        return "labeled".equals(action());
    }

    default boolean isUnlabeled() {
        return "unlabeled".equals(action());
    }

    default boolean isAssigned() {
        return "assigned".equals(action());
    }

    default boolean isUnassigned() {
        return "unassigned".equals(action());
    }

    default boolean isCreated() {
        return "created".equals(action());
    }

    default boolean isDismissed() {
        return "dismissed".equals(action());
    }

    default boolean isResolved() {
        return "resolved".equals(action());
    }

    default boolean isUnresolved() {
        return "unresolved".equals(action());
    }
}
