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

    /**
     * Upper bound on {@code n} before computing {@code n^4}. {@code hephaestus.agent.max-retries} has no
     * configured ceiling ({@code @PositiveOrZero} only), so a large operator-set value could otherwise
     * reach {@code n} large enough for {@code n^4} to overflow {@code long} (#1368 fix wave, finding #13).
     * 1000^4 (1e12) is comfortably inside {@code long} range and already far beyond {@link #CAP} once
     * capped below, so clamping {@code n} here changes nothing about the OUTPUT for any realistic
     * {@code max-retries} value — it only removes the overflow risk at the input.
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
        long baseSeconds = ((long) n * n * n * n) + 15;
        double jitterMultiplier = 1.0 + ((random.nextDouble() * 2.0 - 1.0) * JITTER_FRACTION);
        // Jitter is applied to the UNCAPPED base, then the cap is enforced AFTER jitter (#1368 fix wave,
        // finding #13) — capping before jitter let the +10% jitter leg push the final value past CAP
        // (e.g. a maxed-out 900s base could jitter up to 990s). Clamping post-jitter guarantees the
        // result never exceeds CAP regardless of jitter direction.
        long jitteredSeconds = Math.round(baseSeconds * jitterMultiplier);
        long cappedSeconds = Math.min(jitteredSeconds, CAP.toSeconds());
        return Duration.ofSeconds(Math.max(1, cappedSeconds));
    }
}
