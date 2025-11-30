package de.tum.in.www1.hephaestus.workspace;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.springframework.lang.NonNull;

@Entity
@Table(name = "repository_to_monitor")
@Getter
@Setter
@NoArgsConstructor
@ToString
public class RepositoryToMonitor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NonNull
    private String nameWithOwner;

    private Instant repositorySyncedAt;
    private Instant labelsSyncedAt;
    private Instant milestonesSyncedAt;
    private Instant collaboratorsSyncedAt;

    // The time up to which issues and pull requests have been synced in the recent sync
    private Instant issuesAndPullRequestsSyncedAt;

    /**
     * The highest issue number discovered in the repository for backfill tracking.
     * Set when backfill starts, used to know when backfill is complete.
     */
    private Integer backfillHighWaterMark;

    /**
     * The last issue number that was successfully backfilled.
     * Backfill works backwards from highWaterMark down to 1.
     * When this reaches 1, backfill is complete.
     */
    private Integer backfillCheckpoint;

    /**
     * When the backfill was last run. Used for cooldown between backfill batches.
     */
    private Instant backfillLastRunAt;

    @ManyToOne
    @JoinColumn(name = "workspace_id")
    @ToString.Exclude
    private Workspace workspace;

    /**
     * Checks if backfill has been initialized (high water mark set).
     * @return true if backfill tracking has been initialized
     */
    public boolean isBackfillInitialized() {
        return backfillHighWaterMark != null;
    }

    /**
     * Checks if backfill is in progress (initialized but not complete).
     * @return true if backfill has started and is not yet complete
     */
    public boolean isBackfillInProgress() {
        if (!isBackfillInitialized()) {
            return false;
        }
        // If high water mark is 0, there's nothing to backfill
        if (backfillHighWaterMark == 0) {
            return false;
        }
        // In progress if checkpoint hasn't reached 0 yet
        return backfillCheckpoint == null || backfillCheckpoint > 0;
    }

    /**
     * Checks if backfill has completed (checkpoint reached 0 or high water mark is 0).
     * Backfill is complete when:
     * - High water mark is 0 (nothing to backfill from the start)
     * - Checkpoint is 0 or less (we've processed down to issue #1)
     * @return true if backfill finished or there was nothing to backfill
     */
    public boolean isBackfillComplete() {
        if (!isBackfillInitialized()) {
            return false; // Not initialized = not complete
        }
        // Complete if nothing to backfill or checkpoint reached/passed end
        return backfillHighWaterMark == 0 || (backfillCheckpoint != null && backfillCheckpoint <= 0);
    }

    /**
     * Returns the number of items remaining to backfill.
     * Since checkpoint represents the next issue number to process (working down to 0),
     * the remaining count is simply the checkpoint value itself.
     * @return items remaining, or 0 if not initialized or complete
     */
    public int getBackfillRemaining() {
        if (!isBackfillInitialized() || backfillHighWaterMark == 0) {
            return 0;
        }
        if (backfillCheckpoint == null) {
            return backfillHighWaterMark;
        }
        return Math.max(0, backfillCheckpoint);
    }
}
