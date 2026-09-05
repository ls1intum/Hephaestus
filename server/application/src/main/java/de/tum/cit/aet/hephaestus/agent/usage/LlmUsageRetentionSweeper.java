package de.tum.cit.aet.hephaestus.agent.usage;

import de.tum.cit.aet.hephaestus.agent.BoundedBatchPass;
import de.tum.cit.aet.hephaestus.core.PrivacyJobMetrics;
import de.tum.cit.aet.hephaestus.core.PrivacyJobMetrics.Job;
import de.tum.cit.aet.hephaestus.core.PrivacyJobMetrics.Outcome;
import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import de.tum.cit.aet.hephaestus.core.runtime.ConditionalOnServerRole;
import java.time.Clock;
import java.time.Instant;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Applies storage limitation to the append-only spend ledger: usage older than
 * {@link LlmUsageProperties#retention} is deleted, in {@link BoundedBatchPass} batches so the
 * recorder appending to the same table is never held up by one unbounded statement.
 */
@ConditionalOnServerRole
@Component
@WorkspaceAgnostic("Retention removes expired usage rows across all workspaces")
public class LlmUsageRetentionSweeper {

    private static final Logger log = LoggerFactory.getLogger(LlmUsageRetentionSweeper.class);

    private final LlmUsageEventRepository repository;
    private final LlmUsageProperties properties;
    private final Clock clock;
    private final BoundedBatchPass pass;
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
        this.pass = new BoundedBatchPass(clock);
        this.transactionTemplate = transactionTemplate;
        this.metrics = metrics;
    }

    /**
     * {@code lockAtMostFor} must stay above {@link BoundedBatchPass#DEFAULT_BUDGET}, or a second replica
     * joins the pass.
     */
    @Scheduled(cron = "0 15 4 * * *")
    @SchedulerLock(name = "llm-usage-retention-sweep", lockAtMostFor = "PT20M", lockAtLeastFor = "PT30S")
    public void sweep() {
        sweepNow();
    }

    /** @return the number of ledger rows this pass deleted */
    public long sweepNow() {
        try {
            BoundedBatchPass.Result result = deleteExpiredInBatches();
            metrics.record(Job.LLM_USAGE_RETENTION, result.truncated() ? Outcome.INCOMPLETE : Outcome.SUCCESS);
            metrics.recordAffected(Job.LLM_USAGE_RETENTION, result.affected());
            return result.affected();
        } catch (RuntimeException e) {
            metrics.record(Job.LLM_USAGE_RETENTION, Outcome.FAILURE);
            throw e;
        }
    }

    /** A truncated pass leaves expired personal data in place, which is why it is not a success. */
    private BoundedBatchPass.Result deleteExpiredInBatches() {
        Instant cutoff = clock.instant().minus(properties.retention());
        BoundedBatchPass.Result result = pass.run(log, "LLM usage", () -> {
            Integer deleted = transactionTemplate.execute(status -> repository.deleteExpired(cutoff, pass.batchSize()));
            return deleted != null ? deleted : 0;
        });
        if (result.affected() > 0) {
            log.info("Retention: deleted {} llm_usage_event row(s) occurring before {}", result.affected(), cutoff);
        }
        return result;
    }
}
