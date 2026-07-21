package de.tum.cit.aet.hephaestus.agent.job;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;
import java.util.random.RandomGenerator;

/**
 * Backoff schedule for a requeued {@code agent_job} row (#1368 hardening): a job requeued after an
 * infra failure, orphan recovery, or worker drain becomes eligible again only after {@code available_at}
 * elapses, instead of instantly re-competing for a claim.
 *
 * <p>Quartic-with-jitter — the Sidekiq/Resque-descended industry default: {@code attempt^4 + 15}
 * seconds, capped at {@link #CAP} so a chronically failing job never waits longer than that, jittered
 * ±10% so a burst of jobs failing at the same instant (e.g. a shared dependency outage) does not then
 * all retry in the same instant again (the thundering-herd failure mode plain exponential backoff has).
 *
 * <p>Without this, a crash-looping job (e.g. a config pointing at a dead LLM endpoint) burns its entire
 * retry budget in seconds — the pressure-test verdict's Tier 1 #4 finding.
 */
final class AgentJobBackoff {

    /** No requeued job waits longer than this, however high its retry count climbs. */
    static final Duration CAP = Duration.ofMinutes(15);

    private static final double JITTER_FRACTION = 0.10;

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
        int n = Math.max(0, attemptNumber);
        long baseSeconds = ((long) n * n * n * n) + 15;
        long cappedSeconds = Math.min(baseSeconds, CAP.toSeconds());
        double jitterMultiplier = 1.0 + ((random.nextDouble() * 2.0 - 1.0) * JITTER_FRACTION);
        long jitteredSeconds = Math.round(cappedSeconds * jitterMultiplier);
        return Duration.ofSeconds(Math.max(1, jitteredSeconds));
    }
}
