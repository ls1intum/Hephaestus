package de.tum.in.www1.hephaestus.activity.backfill;

import java.time.Duration;
import java.time.Instant;

/**
 * Immutable record tracking the progress of an activity event backfill operation.
 *
 * <p>Each backfill method returns this record to provide visibility into:
 * <ul>
 *   <li>How many entities were processed</li>
 *   <li>How many events were successfully created</li>
 *   <li>How many were skipped (already existed or invalid data)</li>
 *   <li>How many failed with errors</li>
 *   <li>Duration of the operation</li>
 * </ul>
 *
 * @param entityType the type of entity being backfilled (e.g., "PullRequest", "Review")
 * @param totalProcessed total number of entities examined
 * @param eventsCreated number of new activity events successfully recorded
 * @param skipped number of entities skipped (null author, duplicate, etc.)
 * @param failed number of entities that failed with errors
 * @param duration time taken to complete this backfill phase
 * @param startedAt when the backfill started
 * @param completedAt when the backfill completed
 */
public record BackfillProgress(
    String entityType,
    long totalProcessed,
    long eventsCreated,
    long skipped,
    long failed,
    Duration duration,
    Instant startedAt,
    Instant completedAt
) {
    /**
     * Creates a new builder for constructing BackfillProgress.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Merges this progress with another, summing all counters.
     * Useful for aggregating progress across multiple phases.
     *
     * @param other the other progress to merge
     * @return a new combined progress record
     */
    public BackfillProgress merge(BackfillProgress other) {
        Instant earliestStart = this.startedAt.isBefore(other.startedAt) ? this.startedAt : other.startedAt;
        Instant latestComplete = this.completedAt.isAfter(other.completedAt) ? this.completedAt : other.completedAt;
        return new BackfillProgress(
            this.entityType + "+" + other.entityType,
            this.totalProcessed + other.totalProcessed,
            this.eventsCreated + other.eventsCreated,
            this.skipped + other.skipped,
            this.failed + other.failed,
            Duration.between(earliestStart, latestComplete),
            earliestStart,
            latestComplete
        );
    }

    /**
     * Returns a human-readable summary of this progress.
     */
    public String summary() {
        return String.format(
            "%s backfill: processed=%d, created=%d, skipped=%d, failed=%d, duration=%s",
            entityType,
            totalProcessed,
            eventsCreated,
            skipped,
            failed,
            formatDuration(duration)
        );
    }

    private String formatDuration(Duration d) {
        long seconds = d.toSeconds();
        if (seconds < 60) {
            return seconds + "s";
        }
        long minutes = seconds / 60;
        long remainingSeconds = seconds % 60;
        return minutes + "m " + remainingSeconds + "s";
    }

    /**
     * Builder for constructing BackfillProgress incrementally.
     */
    public static class Builder {

        private String entityType = "Unknown";
        private long totalProcessed = 0;
        private long eventsCreated = 0;
        private long skipped = 0;
        private long failed = 0;
        private Instant startedAt;
        private Instant completedAt;

        public Builder entityType(String entityType) {
            this.entityType = entityType;
            return this;
        }

        public Builder start() {
            this.startedAt = Instant.now();
            return this;
        }

        public Builder incrementProcessed() {
            this.totalProcessed++;
            return this;
        }

        public Builder incrementCreated() {
            this.eventsCreated++;
            return this;
        }

        public Builder incrementSkipped() {
            this.skipped++;
            return this;
        }

        public Builder incrementFailed() {
            this.failed++;
            return this;
        }

        public Builder addProcessed(long count) {
            this.totalProcessed += count;
            return this;
        }

        public Builder addCreated(long count) {
            this.eventsCreated += count;
            return this;
        }

        public Builder addSkipped(long count) {
            this.skipped += count;
            return this;
        }

        public Builder addFailed(long count) {
            this.failed += count;
            return this;
        }

        /**
         * Get the current count of processed entities.
         * Useful for periodic logging or clearing during batch processing.
         */
        public long getTotalProcessed() {
            return this.totalProcessed;
        }

        public BackfillProgress build() {
            if (this.startedAt == null) {
                this.startedAt = Instant.now();
            }
            this.completedAt = Instant.now();
            Duration duration = Duration.between(startedAt, completedAt);
            return new BackfillProgress(
                entityType,
                totalProcessed,
                eventsCreated,
                skipped,
                failed,
                duration,
                startedAt,
                completedAt
            );
        }
    }
}
