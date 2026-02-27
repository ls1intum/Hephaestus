package de.tum.in.www1.hephaestus.gitprovider.organization.gitlab;

import de.tum.in.www1.hephaestus.gitprovider.repository.Repository;
import java.util.Collections;
import java.util.List;

/**
 * Result of a GitLab group project sync operation.
 * <p>
 * Provides structured feedback about the sync outcome, including partial success
 * scenarios where some projects were synced but others failed.
 *
 * @param status          the overall sync outcome
 * @param synced          successfully synced repositories (unmodifiable)
 * @param pagesCompleted  number of pagination pages successfully fetched
 * @param projectsSkipped number of individual projects that failed to process
 */
public record GitLabSyncResult(
    Status status,
    List<Repository> synced,
    int pagesCompleted,
    int projectsSkipped
) {
    public enum Status {
        /** All projects synced successfully. */
        COMPLETED,
        /** Sync finished but some projects or pages failed. */
        COMPLETED_WITH_ERRORS,
        /** Sync aborted due to critical rate limit exhaustion. */
        ABORTED_RATE_LIMIT,
        /** Sync aborted due to an unrecoverable error. */
        ABORTED_ERROR,
    }

    /** Successful sync with no errors. */
    public static GitLabSyncResult completed(List<Repository> synced, int pagesCompleted) {
        return new GitLabSyncResult(Status.COMPLETED, Collections.unmodifiableList(synced), pagesCompleted, 0);
    }

    /** Sync finished but some projects or pages had errors. */
    public static GitLabSyncResult withErrors(
        List<Repository> synced,
        int pagesCompleted,
        int projectsSkipped
    ) {
        return new GitLabSyncResult(
            Status.COMPLETED_WITH_ERRORS,
            Collections.unmodifiableList(synced),
            pagesCompleted,
            projectsSkipped
        );
    }

    /** Sync aborted (returns whatever was synced before the abort). */
    public static GitLabSyncResult aborted(Status status, List<Repository> syncedSoFar, int pagesCompleted) {
        return new GitLabSyncResult(status, Collections.unmodifiableList(syncedSoFar), pagesCompleted, 0);
    }
}
