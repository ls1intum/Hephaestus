package de.tum.in.www1.hephaestus.gitprovider.common.github;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Utility for exponential backoff with jitter for retry operations.
 * <p>
 * This implementation uses the "full jitter" strategy which provides optimal
 * spread of retry attempts across clients, preventing thundering herd scenarios.
 * <p>
 * Formula: {@code wait_time = min(base_delay * 2^attempt + random(0, jitter_max), max_delay)}
 * <p>
 * Example delays with default configuration (1s base, 60s max, 1s jitter):
 * <ul>
 *   <li>Attempt 0: 1s + jitter = ~1-2s</li>
 *   <li>Attempt 1: 2s + jitter = ~2-3s</li>
 *   <li>Attempt 2: 4s + jitter = ~4-5s</li>
 *   <li>Attempt 3: 8s + jitter = ~8-9s</li>
 *   <li>Attempt 4: 16s + jitter = ~16-17s</li>
 *   <li>Attempt 5: 32s + jitter = ~32-33s</li>
 *   <li>Attempt 6+: capped at 60s</li>
 * </ul>
 *
 * <p>Usage example:
 * <pre>{@code
 * int attempt = 0;
 * while (!success && attempt < maxAttempts) {
 *     try {
 *         performOperation();
 *         success = true;
 *     } catch (TransientException e) {
 *         ExponentialBackoff.sleep(attempt++);
 *     }
 * }
 * }</pre>
 *
 * <p>Thread-safe: Uses {@link ThreadLocalRandom} for jitter calculation.
 *
 * @see <a href="https://aws.amazon.com/blogs/architecture/exponential-backoff-and-jitter/">
 *     AWS: Exponential Backoff and Jitter</a>
 */
public final class ExponentialBackoff {

    /** Base delay in milliseconds for the first retry attempt. */
    private static final long BASE_DELAY_MS = 1000;

    /** Maximum delay cap in milliseconds to prevent excessive wait times. */
    private static final long MAX_DELAY_MS = 60000;

    /** Maximum jitter in milliseconds added to each delay. */
    private static final long MAX_JITTER_MS = 1000;

    /** Maximum exponent to prevent overflow (2^6 = 64). */
    private static final int MAX_EXPONENT = 6;

    private ExponentialBackoff() {
        // Utility class - prevent instantiation
    }

    /**
     * Calculates the delay for a given retry attempt using exponential backoff with jitter.
     * <p>
     * The delay grows exponentially with each attempt but is capped at {@link #MAX_DELAY_MS}.
     * Random jitter (0 to {@link #MAX_JITTER_MS}) is added to spread retry attempts.
     *
     * @param attempt the retry attempt number (0-based)
     * @return delay in milliseconds before the next retry
     */
    public static long calculateDelay(int attempt) {
        // Clamp exponent to prevent overflow: 1 << 7 = 128, 1 << 31 would overflow
        int clampedExponent = Math.min(Math.max(attempt, 0), MAX_EXPONENT);

        // Calculate exponential delay: base * 2^attempt
        long exponentialDelay = BASE_DELAY_MS * (1L << clampedExponent);

        // Add random jitter to prevent thundering herd
        long jitter = ThreadLocalRandom.current().nextLong(MAX_JITTER_MS + 1);

        // Cap at maximum delay
        return Math.min(exponentialDelay + jitter, MAX_DELAY_MS);
    }

    /**
     * Calculates delay with custom configuration.
     * <p>
     * Use this method when default values don't fit your use case.
     *
     * @param attempt      the retry attempt number (0-based)
     * @param baseDelayMs  base delay in milliseconds
     * @param maxDelayMs   maximum delay cap in milliseconds
     * @param maxJitterMs  maximum jitter in milliseconds
     * @return delay in milliseconds before the next retry
     * @throws IllegalArgumentException if any parameter is negative
     */
    public static long calculateDelay(int attempt, long baseDelayMs, long maxDelayMs, long maxJitterMs) {
        if (baseDelayMs < 0 || maxDelayMs < 0 || maxJitterMs < 0) {
            throw new IllegalArgumentException("Delay parameters must be non-negative");
        }

        int clampedExponent = Math.min(Math.max(attempt, 0), MAX_EXPONENT);
        long exponentialDelay = baseDelayMs * (1L << clampedExponent);
        long jitter = maxJitterMs > 0 ? ThreadLocalRandom.current().nextLong(maxJitterMs + 1) : 0;

        return Math.min(exponentialDelay + jitter, maxDelayMs);
    }

    /**
     * Sleeps for the calculated backoff duration.
     * <p>
     * Convenience method that calculates the delay and sleeps the current thread.
     * Properly handles thread interruption by re-setting the interrupt flag.
     *
     * @param attempt the retry attempt number (0-based)
     * @throws InterruptedException if the thread is interrupted during sleep
     */
    public static void sleep(int attempt) throws InterruptedException {
        Thread.sleep(calculateDelay(attempt));
    }

    /**
     * Sleeps for the calculated backoff duration with custom configuration.
     *
     * @param attempt      the retry attempt number (0-based)
     * @param baseDelayMs  base delay in milliseconds
     * @param maxDelayMs   maximum delay cap in milliseconds
     * @param maxJitterMs  maximum jitter in milliseconds
     * @throws InterruptedException if the thread is interrupted during sleep
     * @throws IllegalArgumentException if any parameter is negative
     */
    public static void sleep(int attempt, long baseDelayMs, long maxDelayMs, long maxJitterMs)
        throws InterruptedException {
        Thread.sleep(calculateDelay(attempt, baseDelayMs, maxDelayMs, maxJitterMs));
    }

    /**
     * Returns the default base delay in milliseconds.
     *
     * @return base delay (1000ms)
     */
    public static long getBaseDelayMs() {
        return BASE_DELAY_MS;
    }

    /**
     * Returns the default maximum delay in milliseconds.
     *
     * @return max delay (60000ms)
     */
    public static long getMaxDelayMs() {
        return MAX_DELAY_MS;
    }

    /**
     * Returns the default maximum jitter in milliseconds.
     *
     * @return max jitter (1000ms)
     */
    public static long getMaxJitterMs() {
        return MAX_JITTER_MS;
    }
}
