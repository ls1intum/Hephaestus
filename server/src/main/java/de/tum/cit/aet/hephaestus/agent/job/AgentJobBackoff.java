package de.tum.cit.aet.hephaestus.agent.job;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;
import java.util.random.RandomGenerator;

/**
 * How long a requeued {@code agent_job} row waits before it is eligible for a claim again, so a
 * crash-looping job cannot burn its whole retry budget in seconds.
 *
 * <p>Quartic with jitter, the Sidekiq/Resque-descended default. The jitter is what keeps a burst of
 * jobs that failed together (a shared dependency outage) from retrying together.
 */
final class AgentJobBackoff {

    /** No requeued job waits longer than this, however high its retry count climbs. */
    static final Duration CAP = Duration.ofMinutes(15);

    private static final double JITTER_FRACTION = 0.10;

    private static final long BASE_OFFSET_SECONDS = 15;

    /**
     * Clamps {@code n} before {@code n^4} is computed. {@code hephaestus.agent.max-retries} has no
     * configured ceiling, so an operator-set value could otherwise overflow {@code long}; every value
     * this large is capped to {@link #CAP} anyway, so the clamp cannot change an output.
     */
    private static final int MAX_ATTEMPT_FOR_POWER = 1000;

    private AgentJobBackoff() {}

    /**
     * @param attemptNumber the retry attempt about to be made (i.e. {@code retry_count} AFTER this
     *                       requeue's increment); values {@code <= 0} are treated as attempt 0
     */
    static Duration compute(int attemptNumber) {
        return compute(attemptNumber, ThreadLocalRandom.current());
    }

    /** Seeded-random overload for deterministic unit testing. */
    static Duration compute(int attemptNumber, RandomGenerator random) {
        int n = Math.min(Math.max(0, attemptNumber), MAX_ATTEMPT_FOR_POWER);
        long baseSeconds = ((long) n * n * n * n) + BASE_OFFSET_SECONDS;
        double jitterMultiplier = 1.0 + ((random.nextDouble() * 2.0 - 1.0) * JITTER_FRACTION);
        // Jitter the UNCAPPED base and clamp afterwards, so the upward jitter leg cannot push a
        // near-cap wait past CAP.
        long jitteredSeconds = Math.round(baseSeconds * jitterMultiplier);
        long cappedSeconds = Math.min(jitteredSeconds, CAP.toSeconds());
        return Duration.ofSeconds(Math.max(1, cappedSeconds));
    }
}
