package de.tum.cit.aet.hephaestus.agent.runtime.worker;

import de.tum.cit.aet.hephaestus.agent.job.AgentJobExecutor;
import de.tum.cit.aet.hephaestus.core.runtime.RuntimeRole;
import de.tum.cit.aet.hephaestus.core.runtime.worker.protocol.FrameCodec;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;

/**
 * Wires the worker-runtime substrate beans. Gated by {@link RuntimeRole#WORKER_PROPERTY}
 * (matchIfMissing=true) AND by a non-empty {@code hephaestus.worker.control.endpoint}: the
 * substrate has exactly one runtime path — a WSS-connected worker pod. Monolith dev mode
 * leaves the gate set but never points at an endpoint, so no substrate beans wire and agent
 * jobs route through NATS exactly as before.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = RuntimeRole.WORKER_PROPERTY, havingValue = "true", matchIfMissing = true)
@org.springframework.boot.autoconfigure.condition.ConditionalOnExpression(
    "'${hephaestus.worker.control.endpoint:}'.length() > 0"
)
public class WorkerConfiguration {

    /**
     * Single-thread daemon scheduler shared by the capacity reporter. Kept off Spring's default
     * {@code TaskScheduler} so worker telemetry can't be starved by application {@code @Scheduled}
     * methods or accidentally inherit their thread-pool tuning.
     */
    @Bean(name = "workerScheduler", destroyMethod = "shutdownNow")
    ScheduledExecutorService workerScheduler() {
        return Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "worker-scheduler");
            t.setDaemon(true);
            return t;
        });
    }

    @Bean
    WorkerCapacityState workerCapacityState(WorkerProperties properties, MeterRegistry meterRegistry) {
        WorkerCapacityState state = new WorkerCapacityState(properties);
        registerCapacityGauges(meterRegistry, state);
        return state;
    }

    private static void registerCapacityGauges(MeterRegistry registry, WorkerCapacityState state) {
        record G(String name, String type, java.util.function.ToDoubleFunction<WorkerCapacityState> f, String desc) {}
        java.util.List.of(
            new G("worker.capacity.total", "review", s -> s.reviewMax(), "Configured maximum concurrent review jobs"),
            new G(
                "worker.capacity.total",
                "mentor",
                s -> s.mentorMax(),
                "Configured maximum concurrent mentor sessions"
            ),
            new G(
                "worker.capacity.in_flight",
                "review",
                s -> s.snapshot().inFlightReview(),
                "Review jobs currently executing"
            ),
            new G(
                "worker.capacity.in_flight",
                "mentor",
                s -> s.snapshot().inFlightMentor(),
                "Mentor sessions currently open"
            ),
            new G("worker.capacity.spare", "review", s -> s.snapshot().spareReview(), "Spare review slots"),
            new G("worker.capacity.spare", "mentor", s -> s.snapshot().spareMentor(), "Spare mentor slots")
        ).forEach(g ->
            Gauge.builder(g.name(), state, g.f())
                .description(g.desc())
                .tag("type", g.type())
                .strongReference(true)
                .register(registry)
        );
    }

    @Bean
    WorkerControlClient workerControlClient(
        WorkerProperties properties,
        FrameCodec frameCodec,
        ObjectMapper objectMapper,
        MeterRegistry meterRegistry
    ) {
        WorkerControlClient client = new WorkerControlClient(properties, frameCodec, objectMapper, meterRegistry);
        Gauge.builder("worker.control.channel.connected", client, c -> c.isConnected() ? 1.0 : 0.0)
            .description("1 when the worker control channel is connected, 0 otherwise")
            .strongReference(true)
            .register(meterRegistry);
        return client;
    }

    @Bean
    @ConditionalOnMissingBean(FrameCodec.class)
    FrameCodec frameCodec(ObjectMapper objectMapper) {
        return new FrameCodec(objectMapper);
    }

    @Bean
    WorkerCapacityReporter workerCapacityReporter(
        WorkerCapacityState state,
        WorkerControlClient client,
        WorkerProperties properties,
        ScheduledExecutorService workerScheduler,
        MeterRegistry meterRegistry
    ) {
        return new WorkerCapacityReporter(state, client, properties, workerScheduler, meterRegistry);
    }

    @Bean
    WorkerControlChannelHealthIndicator workerControlChannelHealthIndicator(
        WorkerControlClient client,
        WorkerProperties properties
    ) {
        return new WorkerControlChannelHealthIndicator(client, properties);
    }

    /**
     * Wire hub-originated {@code CancelJob} frames to the executor's local-cancel path (#1138).
     * Done after singletons are instantiated so the optional executor (only present when agent
     * NATS is enabled) is resolved without creating a hard dependency cycle.
     */
    @Bean
    org.springframework.beans.factory.SmartInitializingSingleton workerCancelHandlerWiring(
        WorkerControlClient client,
        Optional<AgentJobExecutor> executor
    ) {
        return () -> executor.ifPresent(e -> client.setCancelHandler(e::cancelLocalJob));
    }

    @Bean
    WorkerDrainCoordinator workerDrainCoordinator(
        WorkerControlClient client,
        WorkerCapacityState state,
        WorkerProperties properties,
        Optional<AgentJobExecutor> executor,
        ApplicationEventPublisher events,
        MeterRegistry meterRegistry
    ) {
        return new WorkerDrainCoordinator(client, state, properties, executor, events, meterRegistry);
    }
}
