package de.tum.cit.aet.hephaestus.integration.consumer;

import de.tum.cit.aet.hephaestus.integration.handler.IntegrationMessageHandlerRegistry;
import java.time.Instant;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Actuator health probe for the integration-framework NATS consumer surface.
 *
 * <p>The probe is intentionally a readiness indicator, NOT a liveness one — a NATS
 * outage or a flat-lined dispatch counter should keep traffic off the instance but must
 * not crash-loop the pod. The legacy {@code WebhookHealthIndicator} already covers the
 * publisher half of the pipeline; this one closes the consumer half.
 *
 * <h2>Reported details</h2>
 * <ul>
 *   <li>{@code natsConnectionStatus} — string from {@link IntegrationConsumerStats}, or
 *       {@code uninitialised} when no consumer has spun up yet.</li>
 *   <li>{@code activeScopeConsumers} — count of per-scope subscriptions.</li>
 *   <li>{@code installationConsumerActive} — {@code true} once the installation-wide
 *       consumer has started.</li>
 *   <li>{@code handlerCount} — number of {@link IntegrationMessageHandlerRegistry}
 *       bindings.</li>
 *   <li>{@code parserCount} — number of {@code SubjectParser} bindings exposed by the
 *       {@link IntegrationMessageDispatcher}.</li>
 *   <li>{@code lastDispatchAt} / {@code lastNakAt} — ISO-8601 timestamps, omitted when
 *       no event of that kind has happened yet. Operators use these to spot stalled
 *       consumers without waiting for the heartbeat-restart threshold to fire.</li>
 * </ul>
 *
 * <h2>Status decision</h2>
 * <ul>
 *   <li>{@code UP} — connection is reported as {@code CONNECTED} (any case).</li>
 *   <li>{@code OUT_OF_SERVICE} — consumer fleet has not been initialised yet (the
 *       {@link IntegrationConsumerStats} bean is present but no orchestrator has written
 *       to it). Typical on worker / webhook-only pods where the sync consumer is
 *       disabled. We deliberately do NOT report DOWN so the pod stays in the load
 *       balancer for its other roles.</li>
 *   <li>{@code DOWN} — connection is not {@code CONNECTED}.</li>
 * </ul>
 *
 * <h2>Bean wiring</h2>
 * Spring Boot Actuator is an optional dependency in some assemblies — the
 * {@link ConditionalOnClass} guard keeps this bean from preventing context startup in
 * pods that intentionally omit actuator. The {@link IntegrationConsumerStats} bean is a
 * plain unconditional {@code @Component}, so it is always present; we look at its
 * connection-status field to decide whether the consumer has come up.
 *
 * <p><b>TODO (C13):</b> add a {@code IntegrationConsumerHealthIndicatorTest} once the
 * full consumer split lands — that test needs a {@code @SpringBootTest} slice (actuator
 * binding) which is out of scope for the D9 incremental slice.
 */
@Component
@ConditionalOnClass(HealthIndicator.class)
public class IntegrationConsumerHealthIndicator implements HealthIndicator {

    private static final String STATUS_CONNECTED = "CONNECTED";

    private final IntegrationConsumerStats stats;
    private final IntegrationMessageHandlerRegistry handlerRegistry;
    private final IntegrationMessageDispatcher dispatcher;

    public IntegrationConsumerHealthIndicator(
        IntegrationConsumerStats stats,
        IntegrationMessageHandlerRegistry handlerRegistry,
        IntegrationMessageDispatcher dispatcher
    ) {
        this.stats = stats;
        this.handlerRegistry = handlerRegistry;
        this.dispatcher = dispatcher;
    }

    @Override
    public Health health() {
        // Stats present but never written to — typical on worker / webhook-only pods
        // where the orchestrator that updates them is gated off. Report OUT_OF_SERVICE
        // so this surface is excluded from readiness without DOWNing the whole pod.
        if (stats.natsConnectionStatus().isEmpty()) {
            return Health.outOfService()
                .withDetail("reason", "no integration consumer initialised on this runtime role")
                .withDetail("handlerCount", handlerRegistry.handlerCount())
                .withDetail("parserCount", dispatcher.parserCount())
                .build();
        }

        String connectionStatus = stats.natsConnectionStatus().orElseThrow();
        Health.Builder builder = STATUS_CONNECTED.equalsIgnoreCase(connectionStatus) ? Health.up() : Health.down();

        builder
            .withDetail("natsConnectionStatus", connectionStatus)
            .withDetail("activeScopeConsumers", stats.activeScopeConsumerCount())
            .withDetail("installationConsumerActive", stats.installationConsumerActive())
            .withDetail("handlerCount", handlerRegistry.handlerCount())
            .withDetail("parserCount", dispatcher.parserCount());

        stats.lastDispatchAt().map(Instant::toString).ifPresent(ts -> builder.withDetail("lastDispatchAt", ts));
        stats.lastNakAt().map(Instant::toString).ifPresent(ts -> builder.withDetail("lastNakAt", ts));

        return builder.build();
    }
}
