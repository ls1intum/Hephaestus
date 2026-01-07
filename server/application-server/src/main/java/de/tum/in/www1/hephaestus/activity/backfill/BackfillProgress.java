package de.tum.in.www1.hephaestus.activity.backfill;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;

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
 * <h3>Thread Safety</h3>
 * <p>The {@link Builder} uses atomic counters and is safe to use across multiple
 * threads or transactions. This is important because backfill batches run in
 * separate transactions via {@code @Transactional(propagation = REQUIRES_NEW)}.
 *
 * <h3>Progress Interpretation</h3>
 * <ul>
 *   <li><strong>processed</strong>: Total entities examined (may be less than DB count
 *       if using filtered queries)</li>
 *   <li><strong>created</strong>: New events recorded (a single entity may generate
 *       multiple events, e.g., PR opened + merged)</li>
 *   <li><strong>skipped</strong>: Entities with missing data (null author) or duplicates
 *       (events already exist from prior run)</li>
 *   <li><strong>failed</strong>: Entities that threw exceptions during processing</li>
 * </ul>
 *
 * <p><strong>Invariant:</strong> {@code processed == (entities that generated created events)
 * + skipped + failed}. Note that {@code created >= entities} is possible since one
 * entity can generate multiple events.
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
     *
     * <p><strong>Thread Safety:</strong> This builder uses atomic counters and is safe
     * to share across multiple threads or transactions. All increment/add operations
     * are atomic and lock-free.
     *
     * <p><strong>Usage Pattern:</strong>
     * <pre>{@code
     * BackfillProgress.Builder progress = BackfillProgress.builder()
     *     .entityType("PullRequest")
     *     .start();
     *
     * // Can be called from multiple threads/transactions
     * progress.incrementProcessed();
     * progress.incrementCreated();
     *
     * // When done, build() captures final snapshot
     * BackfillProgress result = progress.build();
     * }</pre>
     */
    public static class Builder {

        private String entityType = "Unknown";
        private final AtomicLong totalProcessed = new AtomicLong(0);
        private final AtomicLong eventsCreated = new AtomicLong(0);
        private final AtomicLong skipped = new AtomicLong(0);
        private final AtomicLong failed = new AtomicLong(0);
        private volatile Instant startedAt;

        /**
         * Sets the entity type being backfilled (e.g., "PullRequest", "Review").
         *
         * @param entityType the entity type name
         * @return this builder for chaining
         */
        public Builder entityType(String entityType) {
            this.entityType = entityType;
            return this;
        }

        /**
         * Marks the start time of the backfill operation.
         *
         * @return this builder for chaining
         */
        public Builder start() {
            this.startedAt = Instant.now();
            return this;
        }

        /**
         * Atomically increments the count of processed entities.
         *
         * <p>Thread-safe: can be called from multiple transactions concurrently.
         *
         * @return this builder for chaining
         */
        public Builder incrementProcessed() {
            this.totalProcessed.incrementAndGet();
            return this;
        }

        /**
         * Atomically increments the count of successfully created events.
         *
         * <p>Thread-safe: can be called from multiple transactions concurrently.
         *
         * @return this builder for chaining
         */
        public Builder incrementCreated() {
            this.eventsCreated.incrementAndGet();
            return this;
        }

        /**
         * Atomically increments the count of skipped entities.
         *
         * <p>Thread-safe: can be called from multiple transactions concurrently.
         *
         * @return this builder for chaining
         */
        public Builder incrementSkipped() {
            this.skipped.incrementAndGet();
            return this;
        }

        /**
         * Atomically increments the count of failed entities.
         *
         * <p>Thread-safe: can be called from multiple transactions concurrently.
         *
         * @return this builder for chaining
         */
        public Builder incrementFailed() {
            this.failed.incrementAndGet();
            return this;
        }

        /**
         * Atomically adds to the processed count.
         *
         * @param count the number to add
         * @return this builder for chaining
         */
        public Builder addProcessed(long count) {
            this.totalProcessed.addAndGet(count);
            return this;
        }

        /**
         * Atomically adds to the created count.
         *
         * @param count the number to add
         * @return this builder for chaining
         */
        public Builder addCreated(long count) {
            this.eventsCreated.addAndGet(count);
            return this;
        }

        /**
         * Atomically adds to the skipped count.
         *
         * @param count the number to add
         * @return this builder for chaining
         */
        public Builder addSkipped(long count) {
            this.skipped.addAndGet(count);
            return this;
        }

        /**
         * Atomically adds to the failed count.
         *
         * @param count the number to add
         * @return this builder for chaining
         */
        public Builder addFailed(long count) {
            this.failed.addAndGet(count);
            return this;
        }

        /**
         * Gets the current count of processed entities.
         *
         * <p>Useful for periodic logging during long-running operations.
         *
         * @return current processed count
         */
        public long getTotalProcessed() {
            return this.totalProcessed.get();
        }

        /**
         * Gets the current count of failed entities.
         *
         * <p>Useful for checking if errors occurred during processing.
         *
         * @return current failed count
         */
        public long getFailed() {
            return this.failed.get();
        }

        /**
         * Builds an immutable {@link BackfillProgress} snapshot.
         *
         * <p>Captures the current counter values at the moment of invocation.
         * The builder can continue to be used after calling build().
         *
         * @return immutable progress record
         */
        public BackfillProgress build() {
            Instant start = this.startedAt;
            if (start == null) {
                start = Instant.now();
            }
            Instant completed = Instant.now();
            Duration duration = Duration.between(start, completed);
            return new BackfillProgress(
                entityType,
                totalProcessed.get(),
                eventsCreated.get(),
                skipped.get(),
                failed.get(),
                duration,
                start,
                completed
            );
        }
    }
}
