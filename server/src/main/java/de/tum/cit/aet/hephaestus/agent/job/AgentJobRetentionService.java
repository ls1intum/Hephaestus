package de.tum.cit.aet.hephaestus.agent.job;

import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
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
 * Bounds {@code agent_job} growth (#1368 hardening — pressure-test verdict Tier 1 #1): with no
 * retention, a busy instance accumulates roughly 3.65M rows/year at 10k jobs/day, each carrying up to
 * 64KB of {@code container_logs}. Two passes, mirroring {@code integration.core.sync.SyncJobService}'s
 * retention style:
 *
 * <ol>
 *   <li><b>Strip</b> — TERMINAL rows older than {@link AgentProperties#payloadRetention} have their
 *       heavy payload columns ({@code container_logs}, {@code output}) set to {@code NULL}. The row
 *       (status, timing, LLM usage aggregates, delivery outcome) survives — only the bulky diagnostic
 *       payload goes.</li>
 *   <li><b>Delete</b> — TERMINAL rows older than {@link AgentProperties#rowRetention} are removed
 *       outright.</li>
 * </ol>
 *
 * <p>Both are batched (fixed {@link #BATCH_SIZE} per UPDATE/DELETE, looped until a batch returns fewer
 * than {@link #BATCH_SIZE} rows) rather than one unbounded statement — a single multi-million-row
 * UPDATE/DELETE would hold locks and generate WAL/dead-tuple pressure for an unacceptably long single
 * transaction (the brandur.org/postgres-queues dead-tuple death spiral this queue design otherwise
 * avoids by keeping every OTHER transaction span short).
 */
@Component
@ConditionalOnProperty(prefix = "hephaestus.agent", name = "enabled", havingValue = "true")
@WorkspaceAgnostic("Cross-workspace retention sweep; caller is @WorkspaceAgnostic maintenance job")
public class AgentJobRetentionService {

    private static final Logger log = LoggerFactory.getLogger(AgentJobRetentionService.class);

    /** Rows touched per UPDATE/DELETE statement — bounds each individual transaction's duration/lock time. */
    private static final int BATCH_SIZE = 500;

    /**
     * Wall-clock budget per pass (strip, then delete — #1368 fix wave, finding #11): on a fresh backlog
     * (e.g. retention just enabled against an already-large table) the batch loop could otherwise run for
     * a very long time in one {@link #runRetention()} invocation. Stopping early once the budget is spent
     * just means the remainder is worked off on the NEXT scheduled run (6h later) instead of blocking this
     * one indefinitely — retention is a slow-moving background process, not a deadline-bound one.
     */
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
     * Daily-ish cadence (every 6h) is plenty for a slow-moving retention window measured in days.
     *
     * <p>{@code @SchedulerLock} single-flights this across replicas (#1368 fix wave, finding #11):
     * without it, every server-role replica fires this on its own 6h timer, all racing the same batched
     * UPDATE/DELETE — each replica's batch loop mostly finds nothing left to do (wasted round-trips) or,
     * worse, several replicas' batches interleave against the same backlog, multiplying lock/WAL pressure
     * for no extra throughput (the batches are already serialized by row-level contention, not
     * parallelizable). {@code lockAtMostFor} covers both passes' {@link #MAX_PASS_DURATION} budgets plus
     * headroom; a crashed replica's stale lock still auto-clears well before the next 6h run.
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
                jobRepository.deleteTerminalRowsOlderThan(cutoff, BATCH_SIZE)
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
