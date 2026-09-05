package de.tum.cit.aet.hephaestus.agent;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.function.IntSupplier;
import org.slf4j.Logger;

/**
 * Runs one retention batch after another until a batch comes back short, so no single statement holds
 * locks or generates WAL/dead-tuple pressure long enough to hurt whatever else writes the table.
 *
 * @param clock read between batches, so a caller's fixed clock keeps a pass deterministic
 * @param batchSize rows one statement may touch; a batch that touches fewer ends the pass
 * @param budget wall-clock allowance per pass, checked between full batches
 */
public record BoundedBatchPass(Clock clock, int batchSize, Duration budget) {

    public static final int DEFAULT_BATCH_SIZE = 500;
    public static final Duration DEFAULT_BUDGET = Duration.ofMinutes(5);

    public BoundedBatchPass(Clock clock) {
        this(clock, DEFAULT_BATCH_SIZE, DEFAULT_BUDGET);
    }

    /**
     * One pass. {@code truncated} means the budget ended it with rows still eligible, so the backlog
     * outlives the pass: a deployment whose every pass ends this way keeps expired data forever.
     *
     * @param affected rows the whole pass touched, including the batch that ran out of budget
     */
    public record Result(long affected, boolean truncated) {}

    /**
     * @param log the caller's logger, so the budget warning stays under the class an operator filters on
     * @param name what the pass does, for that warning
     * @param batch runs one statement bounded to {@link #batchSize} rows and returns how many it touched
     */
    public Result run(Logger log, String name, IntSupplier batch) {
        Instant deadline = clock.instant().plus(budget);
        long total = 0;
        int affected;
        do {
            affected = batch.getAsInt();
            total += affected;
            if (affected == batchSize && clock.instant().isAfter(deadline)) {
                log.warn(
                        "Retention: {} pass hit its {} time budget with backlog remaining — resuming next run",
                        name,
                        budget);
                return new Result(total, true);
            }
        } while (affected == batchSize);
        return new Result(total, false);
    }
}
