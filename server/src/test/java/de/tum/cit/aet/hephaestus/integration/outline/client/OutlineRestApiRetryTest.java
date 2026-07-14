package de.tum.cit.aet.hephaestus.integration.outline.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import io.github.resilience4j.retry.Retry;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

/**
 * The {@code outlineRestApiRetry} decorator produced by {@link OutlineResilienceConfig}. Proves the parsed
 * {@code Retry-After} hint is <em>live</em>: a 429 carrying a hint waits exactly that long (not the ~1s
 * exponential backoff), a transient 5xx retries on backoff, exhausted retries rethrow the original 429,
 * and a permanent 4xx is never retried.
 */
class OutlineRestApiRetryTest extends BaseUnitTest {

    private final Retry retry = new OutlineResilienceConfig().outlineRestApiRetry();

    @Test
    void honorsRetryAfterHintAsTheBackoffInterval() {
        AtomicInteger attempts = new AtomicInteger();
        Supplier<String> supplier = () -> {
            if (attempts.getAndIncrement() == 0) {
                throw new OutlineRateLimitedException(Duration.ofMillis(200), new RuntimeException("429"));
            }
            return "ok";
        };

        long start = System.nanoTime();
        String result = retry.executeSupplier(supplier);
        long elapsedMs = (System.nanoTime() - start) / 1_000_000L;

        assertThat(result).isEqualTo("ok");
        assertThat(attempts.get()).isEqualTo(2);
        // ~200ms hint honored, cleanly below the [500ms, 1500ms] exponential-backoff floor.
        assertThat(elapsedMs).isBetween(150L, 450L);
    }

    @Test
    void retriesTransientServerErrorOnExponentialBackoff() {
        AtomicInteger attempts = new AtomicInteger();
        Supplier<String> supplier = () -> {
            if (attempts.getAndIncrement() == 0) {
                throw new OutlineApiException(
                    "Outline /x failed (HTTP 503)",
                    new RuntimeException(),
                    /* retryable */ true
                );
            }
            return "ok";
        };

        long start = System.nanoTime();
        String result = retry.executeSupplier(supplier);
        long elapsedMs = (System.nanoTime() - start) / 1_000_000L;

        assertThat(result).isEqualTo("ok");
        assertThat(attempts.get()).isEqualTo(2);
        // No Retry-After: falls back to the exponential backoff whose first interval is ≥500ms.
        assertThat(elapsedMs).isGreaterThanOrEqualTo(400L);
    }

    @Test
    void exhaustsBoundedAttemptsThenRethrowsTheRateLimit() {
        AtomicInteger attempts = new AtomicInteger();
        Supplier<String> supplier = () -> {
            attempts.incrementAndGet();
            throw new OutlineRateLimitedException(Duration.ofMillis(1), new RuntimeException("429"));
        };

        assertThatThrownBy(() -> retry.executeSupplier(supplier)).isInstanceOf(OutlineRateLimitedException.class);
        assertThat(attempts.get()).isEqualTo(3); // maxAttempts
    }

    @Test
    void doesNotRetryPermanentClientError() {
        AtomicInteger attempts = new AtomicInteger();
        Supplier<String> supplier = () -> {
            attempts.incrementAndGet();
            throw new OutlineApiException("Outline /x failed (HTTP 404)"); // retryable = false
        };

        assertThatThrownBy(() -> retry.executeSupplier(supplier)).isInstanceOf(OutlineApiException.class);
        assertThat(attempts.get()).isEqualTo(1);
    }
}
