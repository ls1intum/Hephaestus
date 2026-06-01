package de.tum.cit.aet.hephaestus.integration.core.consumer;

import de.tum.cit.aet.hephaestus.core.runtime.RuntimeRole;
import de.tum.cit.aet.hephaestus.integration.core.handler.IntegrationMessageHandlerRegistry;
import java.time.Instant;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Actuator health probe for the integration-framework NATS consumer surface.
 *
 * <p>Wired into the {@code readiness} health group in {@code application.yml} —
 * a NATS outage flips the pod's {@code /actuator/health/readiness} probe to DOWN,
 * k8s pulls traffic, but liveness stays green so the pod is not crash-looped. The
 * legacy {@code WebhookHealthIndicator} covers the publisher half of the pipeline;
 * this one closes the consumer half.
 *
 * <p><b>Runtime-role guard.</b> The indicator is registered ONLY on pods that run
 * the integration consumer ({@code hephaestus.runtime.server.enabled=true},
 * matching {@code IntegrationNatsConsumer}'s own {@code @ConditionalOnProperty}).
 * Worker / webhook-only / specs pods never produce this indicator → it cannot
 * pull them out of readiness with an OUT_OF_SERVICE for a consumer that was
 * never supposed to start. (Spring Boot's readiness group composite uses
 * {@code SimpleStatusAggregator} where OUT_OF_SERVICE dominates UP and maps to
 * HTTP 503; previous wiring caused webhook-pod readiness to fail permanently.)
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
 *   <li>{@code UP} — connection is {@code CONNECTED}, or the consumer is intentionally
 *       disabled by config ({@code DISABLED}). A disabled consumer is a valid runtime
 *       (the webhook pod owns consumption) and must not 503 the app-server.</li>
 *   <li>{@code OUT_OF_SERVICE} — consumer fleet has not been initialised yet (the
 *       {@link IntegrationConsumerStats} bean is present but no orchestrator has written
 *       to it).</li>
 *   <li>{@code DOWN} — consumer is enabled but not {@code CONNECTED}.</li>
 * </ul>
 *
 * <h2>Bean wiring</h2>
 * Spring Boot Actuator is an optional dependency in some assemblies — the
 * {@link ConditionalOnClass} guard keeps this bean from preventing context startup in
 * pods that intentionally omit actuator. The {@link IntegrationConsumerStats} bean is a
 * plain unconditional {@code @Component}, so it is always present; we look at its
 * connection-status field to decide whether the consumer has come up.
 */
@Component
@ConditionalOnClass(HealthIndicator.class)
@ConditionalOnProperty(name = RuntimeRole.SERVER_PROPERTY, havingValue = "true", matchIfMissing = true)
public class IntegrationConsumerHealthIndicator implements HealthIndicator {

    private static final String STATUS_CONNECTED = "CONNECTED";
    private static final String STATUS_DISABLED = "DISABLED";

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
        boolean healthy =
            STATUS_CONNECTED.equalsIgnoreCase(connectionStatus) || STATUS_DISABLED.equalsIgnoreCase(connectionStatus);
        Health.Builder builder = healthy ? Health.up() : Health.down();

        builder
            .withDetail("natsConnectionStatus", connectionStatus)
            .withDetail("activeScopeConsumers", stats.activeScopeConsumerCount())
            .withDetail("installationConsumerActive", stats.installationConsumerActive())
            .withDetail("handlerCount", handlerRegistry.handlerCount())
            .withDetail("parserCount", dispatcher.parserCount());

        stats
            .lastDispatchAt()
            .map(Instant::toString)
            .ifPresent(ts -> builder.withDetail("lastDispatchAt", ts));
        stats
            .lastNakAt()
            .map(Instant::toString)
            .ifPresent(ts -> builder.withDetail("lastNakAt", ts));

        return builder.build();
    }
}
