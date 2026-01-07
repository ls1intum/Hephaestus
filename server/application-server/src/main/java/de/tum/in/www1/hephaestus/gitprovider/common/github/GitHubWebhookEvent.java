package de.tum.in.www1.hephaestus.gitprovider.common.github;

import de.tum.in.www1.hephaestus.gitprovider.repository.github.dto.GitHubRepositoryRefDTO;
import org.springframework.lang.Nullable;

/**
 * Common interface for all GitHub webhook event DTOs.
 * Subinterfaces provide event-specific action types.
 */
public interface GitHubWebhookEvent {
    /** The webhook action string (e.g., "opened", "closed"). */
    String action();

    /** The repository where this event occurred (null for installation events). */
    @Nullable
    GitHubRepositoryRefDTO repository();
}
