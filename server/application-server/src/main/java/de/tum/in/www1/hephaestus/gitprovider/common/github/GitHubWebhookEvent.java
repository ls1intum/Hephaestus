package de.tum.in.www1.hephaestus.gitprovider.common.github;

import de.tum.in.www1.hephaestus.gitprovider.repository.github.dto.GitHubRepositoryRefDTO;
import org.springframework.lang.Nullable;

/**
 * Common interface for all GitHub webhook event DTOs.
 *
 * <p>Each webhook event provides:
 * <ul>
 *   <li>{@link #action()} - the raw action string from the JSON payload</li>
 *   <li>{@link #actionType()} - type-safe enum for the action (recommended for switch expressions)</li>
 *   <li>{@link #repository()} - the repository where the event occurred (null for installation events)</li>
 * </ul>
 *
 * <p>Example usage with pattern matching:
 * <pre>{@code
 * switch (event.actionType()) {
 *     case GitHubEventAction.PullRequest.OPENED -> handleOpened(event);
 *     case GitHubEventAction.PullRequest.CLOSED -> handleClosed(event);
 *     // compiler ensures exhaustive handling
 * }
 * }</pre>
 *
 * @see GitHubEventType for the event type enum (issues, pull_request, etc.)
 * @see GitHubEventAction for type-safe action enums per event type
 */
public interface GitHubWebhookEvent {
    /** The raw webhook action string from the JSON payload (e.g., "opened", "closed"). */
    String action();

    /**
     * Returns the type-safe action enum for this event.
     *
     * <p>Each event DTO returns its specific action enum type:
     * <ul>
     *   <li>GitHubIssueEventDTO returns {@link GitHubEventAction.Issue}</li>
     *   <li>GitHubPullRequestEventDTO returns {@link GitHubEventAction.PullRequest}</li>
     *   <li>etc.</li>
     * </ul>
     *
     * @return the typed action enum, never null (returns UNKNOWN for unrecognized actions)
     */
    GitHubEventAction actionType();

    /** The repository where this event occurred (null for installation events). */
    @Nullable
    GitHubRepositoryRefDTO repository();
}
