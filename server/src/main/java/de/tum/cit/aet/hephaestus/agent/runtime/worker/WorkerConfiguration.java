package de.tum.cit.aet.hephaestus.agent.runtime.worker;

import de.tum.cit.aet.hephaestus.agent.job.AgentJobExecutor;
import de.tum.cit.aet.hephaestus.agent.runtime.worker.session.WorkerSessionDispatcher;
import de.tum.cit.aet.hephaestus.agent.runtime.worker.session.mentor.MentorSessionRunner;
import de.tum.cit.aet.hephaestus.core.runtime.RuntimeRole;
import de.tum.cit.aet.hephaestus.core.runtime.worker.protocol.FrameCodec;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
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
@EnableConfigurationProperties(WorkerProperties.class)
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
        org.springframework.beans.factory.ObjectProvider<WorkerSessionDispatcher> dispatcherProvider,
        ObjectMapper objectMapper,
        MeterRegistry meterRegistry
    ) {
        WorkerControlClient client = new WorkerControlClient(
            properties,
            frameCodec,
            dispatcherProvider,
            objectMapper,
            meterRegistry
        );
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
    MentorSessionRunner mentorSessionRunner(
        WorkerControlPublisher publisher,
        WorkerCapacityState capacityState,
        Optional<de.tum.cit.aet.hephaestus.agent.sandbox.spi.InteractiveSandboxService> sandboxService,
        ObjectMapper objectMapper,
        MeterRegistry meterRegistry
    ) {
        return new MentorSessionRunner(publisher, capacityState, sandboxService, objectMapper, meterRegistry);
    }

    @Bean
    WorkerCapacityReporter workerCapacityReporter(
        WorkerCapacityState state,
        WorkerControlPublisher publisher,
        WorkerProperties properties,
        ScheduledExecutorService workerScheduler,
        MeterRegistry meterRegistry
    ) {
        return new WorkerCapacityReporter(state, publisher, properties, workerScheduler, meterRegistry);
    }

    @Bean
    WorkerControlChannelHealthIndicator workerControlChannelHealthIndicator(
        WorkerControlPublisher publisher,
        WorkerProperties properties
    ) {
        return new WorkerControlChannelHealthIndicator(publisher, properties);
    }

    @Bean
    WorkerSessionDispatcher workerSessionDispatcher(MentorSessionRunner mentorSessionRunner) {
        return new WorkerSessionDispatcher(mentorSessionRunner);
    }

    @Bean
    WorkerDrainCoordinator workerDrainCoordinator(
        WorkerControlPublisher publisher,
        WorkerCapacityState state,
        WorkerProperties properties,
        Optional<AgentJobExecutor> executor,
        Optional<WorkerSessionDispatcher> sessionDispatcher,
        ApplicationEventPublisher events,
        MeterRegistry meterRegistry
    ) {
        return new WorkerDrainCoordinator(
            publisher,
            state,
            properties,
            executor,
            sessionDispatcher,
            events,
            meterRegistry
        );
    }
}
