package de.tum.cit.aet.hephaestus.agent.job;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;
import java.util.random.RandomGenerator;

// Jitter separates retries after a shared dependency outage.
final class ReviewJobBackoff {

    static final Duration CAP = Duration.ofMinutes(15);

    private static final double JITTER_FRACTION = 0.10;

    private static final long BASE_OFFSET_SECONDS = 15;

    // Clamp before exponentiation because max-retries has no ceiling and n^4 can overflow long.
    private static final int MAX_ATTEMPT_FOR_POWER = 1000;

    private ReviewJobBackoff() {}

    /**
     * @param attemptNumber the retry attempt about to be made (i.e. {@code attempt} AFTER this
     *                       requeue's increment); values {@code <= 0} are treated as attempt 0
     */
    static Duration compute(int attemptNumber) {
        return compute(attemptNumber, ThreadLocalRandom.current());
    }

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
