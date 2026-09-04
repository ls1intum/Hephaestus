package de.tum.cit.aet.hephaestus.agent.usage;

import de.tum.cit.aet.hephaestus.core.PrivacyJobMetrics;
import de.tum.cit.aet.hephaestus.core.PrivacyJobMetrics.Job;
import de.tum.cit.aet.hephaestus.core.PrivacyJobMetrics.Outcome;
import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import de.tum.cit.aet.hephaestus.core.runtime.ConditionalOnServerRole;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Applies storage limitation to the append-only spend ledger: usage older than
 * {@link LlmUsageProperties#retention} is deleted.
 *
 * <p>Batched rather than issued as one unbounded statement, so no single transaction holds locks or
 * generates WAL/dead-tuple pressure long enough to hurt the recorder appending to the same table.
 */
@ConditionalOnServerRole
@Component
@WorkspaceAgnostic("Retention removes expired usage rows across all workspaces")
public class LlmUsageRetentionSweeper {

    private static final Logger log = LoggerFactory.getLogger(LlmUsageRetentionSweeper.class);

    private static final int BATCH_SIZE = 500;

    private static final Duration MAX_PASS_DURATION = Duration.ofMinutes(5);

    private final LlmUsageEventRepository repository;
    private final LlmUsageProperties properties;
    private final Clock clock;
    private final TransactionTemplate transactionTemplate;
    private final PrivacyJobMetrics metrics;

    public LlmUsageRetentionSweeper(
            LlmUsageEventRepository repository,
            LlmUsageProperties properties,
            Clock clock,
            TransactionTemplate transactionTemplate,
            PrivacyJobMetrics metrics) {
        this.repository = repository;
        this.properties = properties;
        this.clock = clock;
        this.transactionTemplate = transactionTemplate;
        this.metrics = metrics;
    }

    /** {@code lockAtMostFor} must stay above {@link #MAX_PASS_DURATION}, or a second replica joins the pass. */
    @Scheduled(cron = "0 15 4 * * *")
    @SchedulerLock(name = "llm-usage-retention-sweep", lockAtMostFor = "PT20M", lockAtLeastFor = "PT30S")
    public void sweep() {
        sweepNow();
    }

    /** @return the number of ledger rows this pass deleted */
    public long sweepNow() {
        try {
            Pass pass = deleteExpiredInBatches();
            metrics.record(Job.LLM_USAGE_RETENTION, pass.truncated() ? Outcome.INCOMPLETE : Outcome.SUCCESS);
            metrics.recordAffected(Job.LLM_USAGE_RETENTION, pass.deleted());
            return pass.deleted();
        } catch (RuntimeException e) {
            metrics.record(Job.LLM_USAGE_RETENTION, Outcome.FAILURE);
            throw e;
        }
    }

    /**
     * One pass over the ledger. {@code truncated} means the time budget ended it with rows still
     * eligible, which is not a success: a deployment whose every pass ends this way keeps expired
     * personal data forever.
     */
    private record Pass(long deleted, boolean truncated) {}

    private Pass deleteExpiredInBatches() {
        Instant cutoff = clock.instant().minus(properties.retention());
        Instant deadline = clock.instant().plus(MAX_PASS_DURATION);
        long total = 0;
        int batchDeleted;
        do {
            Integer result = transactionTemplate.execute(status -> repository.deleteExpired(cutoff, BATCH_SIZE));
            batchDeleted = result != null ? result : 0;
            total += batchDeleted;
            if (batchDeleted == BATCH_SIZE && clock.instant().isAfter(deadline)) {
                log.warn(
                        "Retention: LLM usage pass hit its {} time budget with backlog remaining — resuming next run",
                        MAX_PASS_DURATION);
                return new Pass(total, true);
            }
        } while (batchDeleted == BATCH_SIZE);
        if (total > 0) {
            log.info("Retention: deleted {} llm_usage_event row(s) occurring before {}", total, cutoff);
        }
        return new Pass(total, false);
    }
}
