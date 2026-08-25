package de.tum.cit.aet.hephaestus.integration.core.consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.integration.core.handler.IntegrationMessageHandlerRegistry;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.Status;

/**
 * Pins the readiness-probe contract of {@link IntegrationConsumerHealthIndicator}.
 * The indicator is added to the {@code readiness} health group in
 * {@code application.yml}; UP/DOWN here translates directly into k8s pulling/restoring
 * traffic to the pod.
 */
class IntegrationConsumerHealthIndicatorTest extends BaseUnitTest {

    private static IntegrationConsumerHealthIndicator indicator(IntegrationConsumerStats stats) {
        IntegrationMessageHandlerRegistry registry = mock(IntegrationMessageHandlerRegistry.class);
        when(registry.handlerCount()).thenReturn(32);
        IntegrationMessageDispatcher dispatcher = mock(IntegrationMessageDispatcher.class);
        when(dispatcher.parserCount()).thenReturn(4);
        return new IntegrationConsumerHealthIndicator(stats, registry, dispatcher);
    }

    @Nested
    @DisplayName("OUT_OF_SERVICE when consumer never initialised")
    class OutOfService {

        @Test
        void noConnectionStatus_yetReportsOutOfService() {
            IntegrationConsumerStats stats = mock(IntegrationConsumerStats.class);
            when(stats.natsConnectionStatus()).thenReturn(Optional.empty());

            Health health = indicator(stats).health();

            assertThat(health.getStatus()).isEqualTo(Status.OUT_OF_SERVICE);
            assertThat(health.getDetails())
                .containsEntry("handlerCount", 32)
                .containsEntry("parserCount", 4)
                .containsKey("reason");
        }
    }

    @Nested
    @DisplayName("UP when NATS reports CONNECTED")
    class Up {

        @Test
        void connected_reportsUpWithDetails() {
            IntegrationConsumerStats stats = mock(IntegrationConsumerStats.class);
            when(stats.natsConnectionStatus()).thenReturn(Optional.of("CONNECTED"));
            when(stats.activeScopeConsumerCount()).thenReturn(7);
            when(stats.installationConsumerActive()).thenReturn(true);
            Instant dispatchTs = Instant.parse("2026-01-01T00:00:00Z");
            when(stats.lastDispatchAt()).thenReturn(Optional.of(dispatchTs));
            when(stats.lastNakAt()).thenReturn(Optional.empty());

            Health health = indicator(stats).health();

            assertThat(health.getStatus()).isEqualTo(Status.UP);
            assertThat(health.getDetails())
                .containsEntry("natsConnectionStatus", "CONNECTED")
                .containsEntry("activeScopeConsumers", 7)
                .containsEntry("installationConsumerActive", true)
                .containsEntry("lastDispatchAt", dispatchTs.toString())
                .doesNotContainKey("lastNakAt");
        }

        @Test
        void caseInsensitiveConnected() {
            IntegrationConsumerStats stats = mock(IntegrationConsumerStats.class);
            when(stats.natsConnectionStatus()).thenReturn(Optional.of("connected"));
            when(stats.activeScopeConsumerCount()).thenReturn(0);
            when(stats.installationConsumerActive()).thenReturn(false);
            when(stats.lastDispatchAt()).thenReturn(Optional.empty());
            when(stats.lastNakAt()).thenReturn(Optional.empty());

            assertThat(indicator(stats).health().getStatus()).isEqualTo(Status.UP);
        }

        @Test
        void disabledConsumer_reportsUp() {
            // A server-role pod with hephaestus.sync.nats.enabled=false reports DISABLED
            // (IntegrationNatsConsumer): the webhook pod owns consumption, so this is a
            // valid runtime and must not 503 the app-server's readiness probe.
            IntegrationConsumerStats stats = mock(IntegrationConsumerStats.class);
            when(stats.natsConnectionStatus()).thenReturn(Optional.of("DISABLED"));
            when(stats.activeScopeConsumerCount()).thenReturn(0);
            when(stats.installationConsumerActive()).thenReturn(false);
            when(stats.lastDispatchAt()).thenReturn(Optional.empty());
            when(stats.lastNakAt()).thenReturn(Optional.empty());

            Health health = indicator(stats).health();

            assertThat(health.getStatus()).isEqualTo(Status.UP);
            assertThat(health.getDetails()).containsEntry("natsConnectionStatus", "DISABLED");
        }
    }

    @Nested
    @DisplayName("DOWN — readiness group pulls traffic")
    class Down {

        @Test
        void anyOtherStatus_reportsDown() {
            IntegrationConsumerStats stats = mock(IntegrationConsumerStats.class);
            when(stats.natsConnectionStatus()).thenReturn(Optional.of("DISCONNECTED"));
            when(stats.activeScopeConsumerCount()).thenReturn(0);
            when(stats.installationConsumerActive()).thenReturn(false);
            when(stats.lastDispatchAt()).thenReturn(Optional.empty());
            when(stats.lastNakAt()).thenReturn(Optional.of(Instant.parse("2026-01-01T00:00:00Z")));

            Health health = indicator(stats).health();

            assertThat(health.getStatus()).isEqualTo(Status.DOWN);
            assertThat(health.getDetails())
                .containsEntry("natsConnectionStatus", "DISCONNECTED")
                .containsKey("lastNakAt");
        }
    }
}
