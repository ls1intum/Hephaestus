package de.tum.in.www1.hephaestus.gitprovider.common.github;

import de.tum.in.www1.hephaestus.gitprovider.repository.github.dto.GitHubRepositoryRefDTO;
import org.springframework.lang.Nullable;

/**
 * Common interface for all GitHub webhook event DTOs.
 */
public interface GitHubWebhookEvent {
    /** The webhook action string (e.g., "opened", "closed"). */
    String action();

    /** The repository where this event occurred (null for installation events). */
    @Nullable
    GitHubRepositoryRefDTO repository();

    /** Get the action as a type-safe enum. */
    default GitHubWebhookAction actionType() {
        return GitHubWebhookAction.fromString(action());
    }

    /** Check if action matches the given type. */
    default boolean isAction(GitHubWebhookAction expectedAction) {
        return actionType() == expectedAction;
    }
}
