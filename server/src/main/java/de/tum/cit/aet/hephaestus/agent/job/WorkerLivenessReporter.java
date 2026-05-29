package de.tum.cit.aet.hephaestus.agent.job;

import de.tum.cit.aet.hephaestus.agent.runtime.worker.WorkerProperties;
import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import de.tum.cit.aet.hephaestus.core.runtime.RuntimeRole;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Self-reports this worker's liveness into {@code worker_registry} on a timer (#1138).
 *
 * <p>Liveness must track the JVM that actually executes jobs (NATS pull + Docker sandbox), not the
 * WSS control channel — a worker can lose WSS while still running jobs, and orphaning those would
 * double-execute them. Driving the heartbeat from the worker itself makes "stale heartbeat" mean
 * "this executor is gone", which is the only safe trigger for {@link AgentJobZombieSweeper} to requeue
 * a RUNNING job to a sibling.
 *
 * <p>Uses a manual scheduler (not {@code @Scheduled}) because {@code @EnableScheduling} is server-role
 * only; this bean runs on worker pods. Worker identity always binds on the worker role independent of
 * the WSS endpoint, so every executing worker is registered. {@code @Order(1)} + a synchronous first
 * beat guarantee the registry row exists before the executor ({@code @Order(2)}) claims its first job,
 * so a freshly-claimed job is never seen as orphaned.
 */
@Component
@ConditionalOnExpression("${hephaestus.agent.nats.enabled:false} and ${" + RuntimeRole.WORKER_PROPERTY + ":true}")
@WorkspaceAgnostic("Fleet-wide worker liveness; not workspace-scoped.")
public class WorkerLivenessReporter {

    private static final Logger log = LoggerFactory.getLogger(WorkerLivenessReporter.class);

    private final WorkerRegistryRepository repository;
    private final TransactionTemplate transactionTemplate;
    private final Duration interval;
    private final String workerId;
    private final Counter heartbeatFailures;

    private volatile int consecutiveFailures;
    private ScheduledExecutorService scheduler;

    public WorkerLivenessReporter(
        WorkerRegistryRepository repository,
        TransactionTemplate transactionTemplate,
        AgentNatsProperties natsProperties,
        WorkerProperties workerProperties,
        MeterRegistry meterRegistry
    ) {
        this.repository = repository;
        this.transactionTemplate = transactionTemplate;
        this.interval = natsProperties.heartbeatInterval();
        this.workerId = workerProperties.resolvedWorkerId();
        this.heartbeatFailures = Counter.builder("worker.liveness.heartbeat.failures")
            .description("Failed worker_registry heartbeat writes (a stalled reporter risks false orphaning)")
            .register(meterRegistry);
    }

    @EventListener(ApplicationReadyEvent.class)
    @Order(1) // before AgentJobExecutor.start() (@Order(2)) so the registry row exists pre-first-claim
    public void start() {
        beat(); // synchronous first heartbeat — registry row present before any job is claimed
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "worker-liveness");
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleAtFixedRate(this::beat, interval.toSeconds(), interval.toSeconds(), TimeUnit.SECONDS);
        log.info("Worker liveness reporter started: workerId={}, interval={}", workerId, interval);
    }

    private void beat() {
        try {
            transactionTemplate.executeWithoutResult(s -> repository.heartbeat(workerId));
            if (consecutiveFailures > 0) {
                log.info(
                    "Worker liveness heartbeat recovered after {} failure(s): workerId={}",
                    consecutiveFailures,
                    workerId
                );
                consecutiveFailures = 0;
            }
        } catch (Exception e) {
            // Visible, not silent: a worker that can't heartbeat will be falsely orphaned and its jobs
            // double-executed, so this must surface (WARN + metric), not hide at DEBUG.
            heartbeatFailures.increment();
            consecutiveFailures++;
            log.warn(
                "Worker liveness heartbeat failed (consecutive={}): workerId={}, error={}",
                consecutiveFailures,
                workerId,
                e.getClass().getSimpleName()
            );
        }
    }

    @PreDestroy
    public void stop() {
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
        // Deliberately do NOT delete the registry row here. Drain is best-effort (cancelInFlight only
        // logs on failure), so a job could still be RUNNING; deleting the row would immediately orphan
        // it and a sibling would re-run it. Stopping the heartbeat lets the lease expire uniformly —
        // genuinely-unfinished jobs are recovered after the 60s lease, the stale row is purged after 1h.
    }
}
