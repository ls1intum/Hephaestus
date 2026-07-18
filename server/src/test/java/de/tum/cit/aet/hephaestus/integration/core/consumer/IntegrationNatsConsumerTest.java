package de.tum.cit.aet.hephaestus.integration.core.consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.integration.core.handler.IntegrationMessageHandler;
import de.tum.cit.aet.hephaestus.integration.core.spi.EventTypeKey;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.core.spi.NatsSubscriptionProvider.NatsSubscriptionInfo;
import de.tum.cit.aet.hephaestus.integration.core.spi.NatsSubscriptionProvider.StreamSubscription;
import de.tum.cit.aet.hephaestus.integration.core.sync.activity.ConnectionActivityRecorder;
import io.nats.client.ConsumerContext;
import io.nats.client.Message;
import io.nats.client.StreamContext;
import io.nats.client.api.DeliverPolicy;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Unit tests for {@link IntegrationNatsConsumer}.
 *
 * <p>Focus is on the small set of pure helpers carved out of the connect / dispatch path:
 *
 * <ul>
 *   <li>{@link IntegrationNatsConsumer#reconnectBackoffMs(int)} — full-jitter
 *       exponential backoff used by the connect-with-retry loop. Bounds + monotonicity
 *       under attempt growth are nailed down so future tuning doesn't accidentally drop
 *       sub-second delays under load.</li>
 * </ul>
 *
 * <p>The orchestrator itself is exercised indirectly by the workspace-lifecycle tests
 * ({@code GitLabWorkspaceInitializationServiceTest}) and the JetStream loop is covered
 * by the existing {@code IntegrationPoisonHandlerTest} +
 * {@code IntegrationMessageDispatcherTest}. A full Spring-context test would re-test
 * the JetStream client, which is out of scope here.
 */
@Tag("unit")
class IntegrationNatsConsumerTest {

    @Test
    void newConsumerStartsAtNewMessagesInsteadOfReplayingStreamHistory() {
        NatsConsumerProperties properties = new NatsConsumerProperties(
            Duration.ofMinutes(5),
            500,
            Duration.ofSeconds(2),
            new NatsConsumerProperties.PoisonProperties(10, Duration.ofSeconds(2), Duration.ofMinutes(5))
        );

        var config = IntegrationNatsConsumer.newConsumerConfiguration(
            new String[] { "slack.>" },
            properties,
            "heph-slack"
        );

        assertThat(config.getDeliverPolicy()).isEqualTo(DeliverPolicy.New);
        assertThat(config.getDurable()).isEqualTo("heph-slack");
    }

    @Nested
    class ReconnectBackoffMs {

        @ParameterizedTest(name = "attempt={0} → delay in [{1}, {2}] ms")
        @CsvSource(
            {
                // attempt, minMs (= base * 2^attempt, no jitter floor), maxMs (= base * 2^attempt + JITTER_MAX, capped at 30_000)
                "0, 1000, 2000",
                "1, 2000, 3000",
                "2, 4000, 5000",
                "3, 8000, 9000",
                "4, 16000, 17000",
                "5, 30000, 30000",
                "6, 30000, 30000",
            }
        )
        void clampedExponentialWithJitter(int attempt, long minExpected, long maxExpected) {
            long delay = IntegrationNatsConsumer.reconnectBackoffMs(attempt);
            assertThat(delay)
                .as("attempt=%d expected to land in [%d, %d] ms (got %d)", attempt, minExpected, maxExpected, delay)
                .isGreaterThanOrEqualTo(minExpected)
                .isLessThanOrEqualTo(maxExpected);
        }

        @Test
        void negativeAttemptClampedToZero() {
            long delay = IntegrationNatsConsumer.reconnectBackoffMs(-5);
            // base=1000ms, jitter up to 1000ms → [1000, 2000]
            assertThat(delay).isBetween(1_000L, 2_000L);
        }

        @Test
        void delayClampedToHardMax() {
            // Try the same call many times to stress the random-jitter path.
            for (int i = 0; i < 1000; i++) {
                long delay = IntegrationNatsConsumer.reconnectBackoffMs(100);
                assertThat(delay).isLessThanOrEqualTo(30_000L);
            }
        }
    }

    @Nested
    class ActivityRecorderHook {

        private static final Long SCOPE_ID = 7L;

        private IntegrationMessageDispatcher dispatcher;
        private ConnectionActivityRecorder activityRecorder;
        private IntegrationNatsConsumer consumer;
        private Message message;

        private IntegrationNatsConsumer newConsumer() {
            dispatcher = mock(IntegrationMessageDispatcher.class);
            activityRecorder = mock(ConnectionActivityRecorder.class);
            message = mock(Message.class);
            when(message.getSubject()).thenReturn("github.acme.repo.issues");
            return new IntegrationNatsConsumer(
                new NatsConnectionProperties(true, "nats://localhost:4222", "heph", 7, null),
                new NatsConsumerProperties(
                    Duration.ofMinutes(5),
                    500,
                    Duration.ofSeconds(2),
                    new NatsConsumerProperties.PoisonProperties(10, Duration.ofMillis(1), Duration.ofSeconds(1))
                ),
                scopeId -> Optional.empty(),
                dispatcher,
                mock(IntegrationPoisonHandler.class),
                new IntegrationConsumerStats(),
                activityRecorder
            );
        }

        @Test
        void recordsActivityOnHandledMessageWithScope() {
            consumer = newConsumer();
            IntegrationMessageHandler handler = mock(IntegrationMessageHandler.class);
            EventTypeKey key = new EventTypeKey(IntegrationKind.GITHUB, "repository.issues");
            when(handler.key()).thenReturn(key);
            when(dispatcher.dispatch("github.acme.repo.issues")).thenReturn(Optional.of(handler));

            consumer.handleMessage(SCOPE_ID, message);

            verify(handler).onMessage(message);
            verify(message).ack();
            verify(activityRecorder).recordEventProcessed(SCOPE_ID, IntegrationKind.GITHUB, "repository.issues");
        }

        @Test
        void skipsRecorderWhenUnmatched() {
            consumer = newConsumer();
            when(dispatcher.dispatch("github.acme.repo.issues")).thenReturn(Optional.empty());

            consumer.handleMessage(SCOPE_ID, message);

            verify(message).ack();
            verifyNoInteractions(activityRecorder);
        }

        @Test
        void skipsRecorderWhenScopeIsNull() {
            consumer = newConsumer();
            IntegrationMessageHandler handler = mock(IntegrationMessageHandler.class);
            when(handler.key()).thenReturn(new EventTypeKey(IntegrationKind.GITHUB, "installation.created"));
            when(dispatcher.dispatch("github.acme.repo.issues")).thenReturn(Optional.of(handler));

            consumer.handleMessage(null, message);

            verify(handler).onMessage(message);
            verifyNoInteractions(activityRecorder);
        }
    }

    /**
     * A scope binds SEVERAL streams now (an SCM stream plus {@code outline}). If the second stream's consumer
     * cannot be created — the common case being that the {@code outline} stream does not exist yet because the
     * webhook pod creates it on ITS boot — the first stream's consumer has already been {@code start()}ed.
     *
     * <p>Two invariants:
     * <ol>
     *   <li>every started consumer is TRACKED, so it can still be stopped/updated — an untracked one runs
     *       forever, is invisible to {@code updateScopeConsumer} ("not running"), and gets duplicated on its
     *       own durable by the next start;</li>
     *   <li>the failure re-arms a retry, so the transient case self-heals rather than needing a restart.</li>
     * </ol>
     */
    @Nested
    class ReconcileScopePartialFailure {

        private static final Long SCOPE_ID = 42L;
        private static final String SCM_STREAM = "github";
        private static final String OUTLINE_STREAM = "outline";

        private FakeFleet fleet;

        private FakeFleet fleetFailingOn(String... failingStreams) {
            fleet = new FakeFleet(Set.of(failingStreams));
            return fleet;
        }

        @AfterEach
        void tearDown() {
            if (fleet != null) {
                fleet.shutdown(); // stops the retry timer + consumer threads
            }
        }

        @Test
        @DisplayName(
            "a consumer started before the failing stream stays tracked (never a running-but-orphaned consumer)"
        )
        void partialFailureCommitsWhatItStarted() {
            FakeFleet consumer = fleetFailingOn(OUTLINE_STREAM);

            assertThatThrownBy(() -> consumer.reconcileScope(SCOPE_ID)).isInstanceOf(IOException.class);

            assertThat(consumer.started).hasSize(1);
            assertThat(consumer.trackedConsumers(SCOPE_ID))
                .as("the started consumer must remain reachable for stop/update")
                .containsExactlyElementsOf(consumer.started);
            assertThat(consumer.trackedConsumers(SCOPE_ID).get(0).streamName()).isEqualTo(SCM_STREAM);
            assertThat(consumer.trackedConsumers(SCOPE_ID).get(0).isRunning()).isTrue();
        }

        @Test
        @DisplayName("a transient stream failure self-heals: the reconcile is retried and the missing consumer appears")
        void failedReconcileIsRetriedUntilItSucceeds() {
            FakeFleet consumer = fleetFailingOn(OUTLINE_STREAM);

            assertThatThrownBy(() -> consumer.reconcileScope(SCOPE_ID)).isInstanceOf(IOException.class);
            assertThat(consumer.trackedConsumers(SCOPE_ID)).hasSize(1);

            // The webhook pod finished booting and created the stream.
            consumer.failingStreams.clear();

            await()
                .atMost(Duration.ofSeconds(10))
                .untilAsserted(() ->
                    assertThat(consumer.trackedConsumers(SCOPE_ID))
                        .as("the re-armed retry must bind the stream that failed the first pass")
                        .hasSize(2)
                );
            assertThat(consumer.trackedConsumers(SCOPE_ID))
                .extracting(ScopeConsumer::streamName)
                .containsExactlyInAnyOrder(SCM_STREAM, OUTLINE_STREAM);
        }

        /**
         * The fleet with its two broker-touching seams stubbed out: consumer creation (which streams exist)
         * and connection establishment. Everything under test — the reconcile bookkeeping, the commit of
         * partial successes, the retry re-arm — is the real code.
         */
        private static class FakeFleet extends IntegrationNatsConsumer {

            private final Set<String> failingStreams;
            private final List<ScopeConsumer> started = new CopyOnWriteArrayList<>();

            FakeFleet(Set<String> failingStreams) {
                super(
                    new NatsConnectionProperties(true, "nats://localhost:4222", "heph", 7, null),
                    new NatsConsumerProperties(
                        Duration.ofMinutes(5),
                        500,
                        Duration.ofSeconds(2),
                        new NatsConsumerProperties.PoisonProperties(10, Duration.ofMillis(1), Duration.ofSeconds(1))
                    ),
                    scopeId ->
                        Optional.of(
                            new NatsSubscriptionInfo(
                                scopeId,
                                List.of(
                                    new StreamSubscription(SCM_STREAM, Set.of("github.acme.>")),
                                    new StreamSubscription(OUTLINE_STREAM, Set.of("outline.acme.>"))
                                )
                            )
                        ),
                    mock(IntegrationMessageDispatcher.class),
                    mock(IntegrationPoisonHandler.class),
                    new IntegrationConsumerStats(),
                    mock(ConnectionActivityRecorder.class)
                );
                this.failingStreams = new ConcurrentSkipListSet<>(failingStreams);
            }

            @Override
            void ensureNatsConnectionEstablished() {
                // no broker in a unit test
            }

            @Override
            ScopeConsumer createScopeConsumer(Long scopeId, StreamSubscription subscription) throws IOException {
                if (failingStreams.contains(subscription.streamName())) {
                    throw new IOException("stream not found: " + subscription.streamName());
                }
                ScopeConsumer scopeConsumer = new ScopeConsumer(
                    scopeId,
                    "heph-scope-" + scopeId + "-" + subscription.streamName(),
                    subscription.streamName(),
                    mock(ConsumerContext.class),
                    mock(StreamContext.class),
                    subscription.subjects().toArray(String[]::new),
                    msg -> {}
                );
                try {
                    scopeConsumer.start();
                } catch (Exception e) {
                    throw new IOException(e);
                }
                started.add(scopeConsumer);
                return scopeConsumer;
            }
        }
    }
}
