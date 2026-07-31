package de.tum.cit.aet.hephaestus.agent.job;

import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import de.tum.cit.aet.hephaestus.core.runtime.ConditionalOnServerRole;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Bounds {@code agent_job} growth in two passes: TERMINAL rows older than
 * {@link AgentProperties#payloadRetention} keep everything but their bulky {@code container_logs} /
 * {@code output}, and unreferenced rows older than {@link AgentProperties#rowRetention} go entirely.
 *
 * <p>Both are batched rather than issued as one unbounded statement, so no single transaction holds
 * locks or generates WAL/dead-tuple pressure long enough to hurt the queue sharing this table.
 */
@ConditionalOnServerRole
@Component
@ConditionalOnProperty(prefix = "hephaestus.agent", name = "enabled", havingValue = "true")
@WorkspaceAgnostic("Cross-workspace retention sweep; caller is @WorkspaceAgnostic maintenance job")
public class AgentJobRetentionService {

    private static final Logger log = LoggerFactory.getLogger(AgentJobRetentionService.class);

    private static final int BATCH_SIZE = 500;

    private static final Duration MAX_PASS_DURATION = Duration.ofMinutes(5);

    private final AgentJobRepository jobRepository;
    private final AgentProperties agentProperties;
    private final TransactionTemplate transactionTemplate;
    private final Counter stripped;
    private final Counter deleted;

    public AgentJobRetentionService(
        AgentJobRepository jobRepository,
        AgentProperties agentProperties,
        TransactionTemplate transactionTemplate,
        MeterRegistry meterRegistry
    ) {
        this.jobRepository = jobRepository;
        this.agentProperties = agentProperties;
        this.transactionTemplate = transactionTemplate;
        this.stripped = Counter.builder("agent.job.retention.stripped")
            .description("Terminal agent_job rows whose heavy payload columns were stripped to NULL")
            .register(meterRegistry);
        this.deleted = Counter.builder("agent.job.retention.deleted")
            .description("Terminal agent_job rows deleted by the retention sweep")
            .register(meterRegistry);
    }

    /**
     * {@code @SchedulerLock} single-flights this across replicas: concurrent passes cannot go faster (the
     * batches serialize on row-level contention anyway) and only multiply lock/WAL pressure.
     * {@code lockAtMostFor} must stay above both passes' {@link #MAX_PASS_DURATION} budgets.
     */
    @Scheduled(fixedDelay = 6, initialDelay = 1, timeUnit = TimeUnit.HOURS)
    @SchedulerLock(name = "agent-job-retention", lockAtMostFor = "PT20M", lockAtLeastFor = "PT10S")
    public void runRetention() {
        stripPayloads();
        deleteOldRows();
    }

    private void stripPayloads() {
        Instant cutoff = Instant.now().minus(agentProperties.payloadRetention());
        Instant deadline = Instant.now().plus(MAX_PASS_DURATION);
        int total = 0;
        int batchUpdated;
        do {
            Integer result = transactionTemplate.execute(status ->
                jobRepository.stripTerminalPayloads(cutoff, BATCH_SIZE)
            );
            batchUpdated = result != null ? result : 0;
            total += batchUpdated;
            if (batchUpdated > 0) {
                stripped.increment(batchUpdated);
            }
            if (batchUpdated == BATCH_SIZE && Instant.now().isAfter(deadline)) {
                log.warn(
                    "Retention: strip pass hit its {} time budget with backlog remaining — resuming next run",
                    MAX_PASS_DURATION
                );
                break;
            }
        } while (batchUpdated == BATCH_SIZE);
        if (total > 0) {
            log.info(
                "Retention: stripped payloads from {} terminal agent_job row(s) completed before {}",
                total,
                cutoff
            );
        }
    }

    private void deleteOldRows() {
        Instant cutoff = Instant.now().minus(agentProperties.rowRetention());
        Instant deadline = Instant.now().plus(MAX_PASS_DURATION);
        int total = 0;
        int batchDeleted;
        do {
            Integer result = transactionTemplate.execute(status ->
                jobRepository.deleteUnreferencedTerminalRowsOlderThan(cutoff, BATCH_SIZE)
            );
            batchDeleted = result != null ? result : 0;
            total += batchDeleted;
            if (batchDeleted > 0) {
                deleted.increment(batchDeleted);
            }
            if (batchDeleted == BATCH_SIZE && Instant.now().isAfter(deadline)) {
                log.warn(
                    "Retention: delete pass hit its {} time budget with backlog remaining — resuming next run",
                    MAX_PASS_DURATION
                );
                break;
            }
        } while (batchDeleted == BATCH_SIZE);
        if (total > 0) {
            log.info("Retention: deleted {} terminal agent_job row(s) completed before {}", total, cutoff);
        }
    }
}
