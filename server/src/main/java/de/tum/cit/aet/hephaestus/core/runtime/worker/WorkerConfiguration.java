package de.tum.cit.aet.hephaestus.core.runtime.worker;

import de.tum.cit.aet.hephaestus.agent.job.AgentJobExecutor;
import de.tum.cit.aet.hephaestus.core.runtime.RuntimeRole;
import de.tum.cit.aet.hephaestus.core.runtime.worker.protocol.FrameCodec;
import de.tum.cit.aet.hephaestus.core.runtime.worker.session.WorkerSessionDispatcher;
import de.tum.cit.aet.hephaestus.core.runtime.worker.session.mentor.MentorSessionRunner;
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
 * Wires the worker-runtime substrate beans. Gated by
 * {@link RuntimeRole#WORKER_PROPERTY} with {@code matchIfMissing=true}: monolith dev mode keeps
 * the worker substrate loaded; production worker pods set the flag explicitly via
 * {@code application-worker.yml}.
 *
 * <p>The {@link WorkerControlPublisher} bean defaults to {@link LoggingWorkerControlPublisher};
 * the WSS-backed {@code WorkerControlClient} replaces it with a {@code @Primary} bean in the
 * follow-up transport commit. Substrate behavior is observable through logs and metrics in the
 * interim.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = RuntimeRole.WORKER_PROPERTY, havingValue = "true", matchIfMissing = true)
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

    private static void registerCapacityGauges(MeterRegistry meterRegistry, WorkerCapacityState state) {
        Gauge.builder("worker.capacity.total", state, s -> s.reviewMax())
            .description("Configured maximum concurrent review jobs")
            .tag("type", "review")
            .register(meterRegistry);
        Gauge.builder("worker.capacity.total", state, s -> s.mentorMax())
            .description("Configured maximum concurrent mentor sessions")
            .tag("type", "mentor")
            .register(meterRegistry);
        Gauge.builder("worker.capacity.in_flight", state, s -> s.snapshot().inFlightReview())
            .description("Review jobs currently executing")
            .tag("type", "review")
            .register(meterRegistry);
        Gauge.builder("worker.capacity.in_flight", state, s -> s.snapshot().inFlightMentor())
            .description("Mentor sessions currently open")
            .tag("type", "mentor")
            .register(meterRegistry);
        Gauge.builder("worker.capacity.spare", state, s -> s.snapshot().spareReview())
            .description("Spare review slots")
            .tag("type", "review")
            .register(meterRegistry);
        Gauge.builder("worker.capacity.spare", state, s -> s.snapshot().spareMentor())
            .description("Spare mentor slots")
            .tag("type", "mentor")
            .register(meterRegistry);
    }

    @Bean
    @ConditionalOnMissingBean(WorkerControlPublisher.class)
    WorkerControlPublisher loggingWorkerControlPublisher() {
        return new LoggingWorkerControlPublisher();
    }

    /**
     * WSS-backed control client. Created only when a control endpoint URL is configured;
     * otherwise the default {@link LoggingWorkerControlPublisher} stays in place (useful for
     * cold-start smoke tests and failure-isolation runs).
     */
    @Bean
    @Primary
    // havingValue+matchIfMissing won't reject empty-string; the SpEL keeps the bean from wiring
    // when HEPHAESTUS_HUB_URL resolves to "" (the application-worker.yml default).
    @org.springframework.boot.autoconfigure.condition.ConditionalOnExpression(
        "'${hephaestus.worker.control.endpoint:}'.length() > 0"
    )
    WorkerControlClient workerControlClient(
        WorkerProperties properties,
        FrameCodec frameCodec,
        org.springframework.beans.factory.ObjectProvider<WorkerSessionDispatcher> dispatcherProvider,
        ObjectMapper objectMapper,
        MeterRegistry meterRegistry
    ) {
        return new WorkerControlClient(properties, frameCodec, dispatcherProvider, objectMapper, meterRegistry);
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
    WorkerControlChannelGaugeBinder workerControlChannelGaugeBinder(
        WorkerControlPublisher publisher,
        MeterRegistry meterRegistry
    ) {
        return new WorkerControlChannelGaugeBinder(publisher, meterRegistry);
    }

    /** {@link MeterRegistry} keeps a weak reference to the source; this holder keeps it alive. */
    static final class WorkerControlChannelGaugeBinder {

        @SuppressWarnings("unused")
        private final WorkerControlPublisher publisher;

        WorkerControlChannelGaugeBinder(WorkerControlPublisher publisher, MeterRegistry meterRegistry) {
            this.publisher = publisher;
            Gauge.builder("worker.control.channel.connected", publisher, p -> p.isConnected() ? 1.0 : 0.0)
                .description("1 when the worker control channel is connected, 0 otherwise")
                .register(meterRegistry);
        }
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
    WorkerSessionDispatcher workerSessionDispatcher(Optional<MentorSessionRunner> mentorSessionRunner) {
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
