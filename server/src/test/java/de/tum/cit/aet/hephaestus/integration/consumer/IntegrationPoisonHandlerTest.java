package de.tum.cit.aet.hephaestus.integration.consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.nats.client.Message;
import io.nats.client.impl.NatsJetStreamMetaData;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * Unit tests for {@link IntegrationPoisonHandler}. Mocks the NATS message + metadata so
 * we can verify NAK delay math, poison ACK threshold, and counter increments without a
 * live JetStream connection.
 */
@DisplayName("IntegrationPoisonHandler NAK / poison policy")
class IntegrationPoisonHandlerTest extends BaseUnitTest {

    private MeterRegistry meterRegistry;
    private NatsConsumerProperties properties;
    private IntegrationPoisonHandler handler;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        properties = new NatsConsumerProperties(
            Duration.ofMinutes(5),
            500,
            Duration.ofSeconds(2),
            new NatsConsumerProperties.PoisonProperties(10, Duration.ofSeconds(2), Duration.ofMinutes(5))
        );
        handler = new IntegrationPoisonHandler(properties, meterRegistry);
    }

    @Nested
    @DisplayName("nakWithBackoff")
    class NakWithBackoff {

        @Test
        @DisplayName("invokes msg.nakWithDelay with exponential backoff on first failure")
        void firstFailureNAKsWithBaseDelay() {
            Message msg = githubMessage(1L, "github.acme.foo.issues");

            handler.nakWithBackoff(msg);

            ArgumentCaptor<Duration> delayCaptor = ArgumentCaptor.forClass(Duration.class);
            verify(msg).nakWithDelay(delayCaptor.capture());
            // attempt=0 → 2s base + up to 1s jitter, clamped at 5m max.
            assertThat(delayCaptor.getValue().toMillis()).isBetween(2_000L, 3_001L);
            verify(msg, never()).ack();
        }

        @Test
        @DisplayName("backoff grows exponentially with redelivery count and stays under max delay")
        void backoffIsExponentialAndCapped() {
            // attempt=2 (3rd delivery) → 2 * 2^2 = 8s + jitter → 8..9s
            Message third = githubMessage(3L, "github.acme.foo.issues");
            handler.nakWithBackoff(third);
            ArgumentCaptor<Duration> capture3 = ArgumentCaptor.forClass(Duration.class);
            verify(third).nakWithDelay(capture3.capture());
            assertThat(capture3.getValue().toMillis()).isBetween(8_000L, 9_001L);

            // attempt=8 → 2 * 256 = 512s, capped at 300s (5m).
            Message ninth = githubMessage(9L, "github.acme.foo.issues");
            handler.nakWithBackoff(ninth);
            ArgumentCaptor<Duration> capture9 = ArgumentCaptor.forClass(Duration.class);
            verify(ninth).nakWithDelay(capture9.capture());
            assertThat(capture9.getValue()).isLessThanOrEqualTo(Duration.ofMinutes(5));
        }

        @Test
        @DisplayName("increments NAK counter tagged with the message kind")
        void nakIncrementsKindTaggedCounter() {
            Message gh = githubMessage(1L, "github.acme.foo.issues");
            Message gl = githubMessage(1L, "gitlab.group.proj.push");

            handler.nakWithBackoff(gh);
            handler.nakWithBackoff(gl);

            assertThat(meterRegistry.counter(IntegrationPoisonHandler.NAK_COUNTER, "kind", "github").count()).isEqualTo(
                1.0
            );
            assertThat(meterRegistry.counter(IntegrationPoisonHandler.NAK_COUNTER, "kind", "gitlab").count()).isEqualTo(
                1.0
            );
        }

        @Test
        @DisplayName("falls back to immediate nak() when nakWithDelay throws")
        void fallsBackToImmediateNakWhenDelayThrows() {
            Message msg = githubMessage(1L, "github.acme.foo.issues");
            doThrow(new IllegalStateException("connection closed")).when(msg).nakWithDelay(any());

            handler.nakWithBackoff(msg);

            verify(msg).nakWithDelay(any());
            verify(msg).nak();
        }

        @Test
        @DisplayName("ignores null message")
        void nullMessageIsTolerated() {
            // No exception, no counter increment — keeps the consumer's error path
            // null-safe so an upstream NPE doesn't get rethrown out of the NAK handler.
            handler.nakWithBackoff(null);
            assertThat(meterRegistry.find(IntegrationPoisonHandler.NAK_COUNTER).counter()).isNull();
        }
    }

    @Nested
    @DisplayName("poison detection at MAX_REDELIVER")
    class PoisonAtThreshold {

        @Test
        @DisplayName("isPoison returns true once deliveredCount >= maxRedeliver")
        void isPoisonAtThreshold() {
            assertThat(handler.isPoison(githubMessage(9L, "github.x.y.z"))).isFalse();
            assertThat(handler.isPoison(githubMessage(10L, "github.x.y.z"))).isTrue();
            assertThat(handler.isPoison(githubMessage(11L, "github.x.y.z"))).isTrue();
        }

        @Test
        @DisplayName("nakWithBackoff at threshold ACKs the message rather than NAKing")
        void thresholdHitAcksAsPoison() {
            Message msg = githubMessage(10L, "github.acme.foo.issues");

            handler.nakWithBackoff(msg);

            // Poison ACK breaks the redelivery loop — we ACK instead of NAK.
            verify(msg).ack();
            verify(msg, never()).nakWithDelay(any());
            assertThat(
                meterRegistry.counter(IntegrationPoisonHandler.POISON_COUNTER, "kind", "github").count()
            ).isEqualTo(1.0);
        }

        @Test
        @DisplayName("explicit ackPoison logs and increments counter even from caller")
        void explicitAckPoisonIncrementsCounter() {
            Message msg = githubMessage(7L, "github.acme.foo.issues");

            handler.ackPoison(msg, "manual dead-letter");

            verify(msg).ack();
            assertThat(
                meterRegistry.counter(IntegrationPoisonHandler.POISON_COUNTER, "kind", "github").count()
            ).isEqualTo(1.0);
        }

        @Test
        @DisplayName("unknown subject prefix tags counter as 'unknown'")
        void unknownPrefixGetsUnknownTag() {
            Message msg = githubMessage(10L, "bitbucket.foo.bar.event");

            handler.nakWithBackoff(msg);

            assertThat(
                meterRegistry.counter(IntegrationPoisonHandler.POISON_COUNTER, "kind", "unknown").count()
            ).isEqualTo(1.0);
        }
    }

    // -------------------------------------------------------------------------
    // helpers
    // -------------------------------------------------------------------------

    /**
     * Build a mocked NATS message with deliveredCount + subject pre-wired. We use
     * {@code mock(...)} rather than {@code @Mock} because each test creates several
     * messages with different states and {@code @Mock} fields would force shared setup.
     */
    private static Message githubMessage(long deliveredCount, String subject) {
        // Use lenient() because not every code path consumes every stub: tests that only
        // exercise the NAK arithmetic skip the subject/streamSequence reads, and tests
        // that exercise poison-ACK skip nakWithDelay. Strict mode would flag those as
        // unnecessary stubbings even though the helper is the right shape for all callers.
        Message msg = mock(Message.class);
        NatsJetStreamMetaData meta = mock(NatsJetStreamMetaData.class);
        lenient().when(msg.metaData()).thenReturn(meta);
        lenient().when(msg.getSubject()).thenReturn(subject);
        lenient().when(meta.deliveredCount()).thenReturn(deliveredCount);
        lenient().when(meta.streamSequence()).thenReturn(42L);
        return msg;
    }
}
