package de.tum.cit.aet.hephaestus.core.auth.export;

import de.tum.cit.aet.hephaestus.core.PrivacyJobMetrics;
import de.tum.cit.aet.hephaestus.core.PrivacyJobMetrics.Job;
import de.tum.cit.aet.hephaestus.core.PrivacyJobMetrics.Outcome;
import de.tum.cit.aet.hephaestus.core.TransactionCallbacks;
import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import de.tum.cit.aet.hephaestus.core.runtime.ConditionalOnServerRole;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * Separate bean from {@code AccountExportService}: self-invocation would bypass the {@link Async}
 * proxy and run the assembly inline on the request thread.
 */
@ConditionalOnServerRole
@Component
@WorkspaceAgnostic("GDPR export generation operates on a single account's data; not workspace-scoped")
public class ExportGenerationWorker {

    private static final Logger log = LoggerFactory.getLogger(ExportGenerationWorker.class);

    /** Retention window for a READY export before the sweep expires it and frees the payload. */
    static final Duration RETENTION = Duration.ofHours(48);

    private final AccountExportRepository accountExportRepository;
    private final ExportBundleAssembler assembler;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final PrivacyJobMetrics metrics;

    public ExportGenerationWorker(
            AccountExportRepository accountExportRepository,
            ExportBundleAssembler assembler,
            ObjectMapper objectMapper,
            Clock clock,
            PrivacyJobMetrics metrics) {
        this.accountExportRepository = accountExportRepository;
        this.assembler = assembler;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.metrics = metrics;
    }

    /**
     * Never throws: the caller is fire-and-forget, so every outcome is recorded on the row and on the
     * privacy-job counters instead.
     */
    @Async
    @Transactional
    public void generate(Long exportId, Long accountId) {
        AccountExport export = accountExportRepository
                .findByIdAndAccountId(exportId, accountId)
                .orElse(null);
        if (export == null) {
            // Row vanished (e.g. account hard-deleted between request and pickup). Nothing to do.
            log.warn("auth.export: generation skipped, export {} for account {} not found", exportId, accountId);
            recordAfterCommit(Outcome.FAILURE);
            return;
        }
        export.setStatus(AccountExport.Status.PROCESSING);
        accountExportRepository.save(export);

        try {
            ExportBundle bundle = assembler.assemble(accountId);
            byte[] payload = objectMapper.writeValueAsBytes(bundle);
            Instant now = Instant.now(clock);
            export.setPayload(payload);
            export.setCompletedAt(now);
            export.setExpiresAt(now.plus(RETENTION));
            export.setStatus(AccountExport.Status.READY);
            accountExportRepository.save(export);
            TransactionCallbacks.afterCommit(() -> {
                metrics.record(Job.EXPORT_GENERATION, Outcome.SUCCESS);
                metrics.recordAffected(Job.EXPORT_GENERATION, 1);
            });
            log.info("auth.export: export {} for account {} READY ({} bytes)", exportId, accountId, payload.length);
        } catch (JacksonException e) {
            fail(export, "serialization_failed");
            log.error("auth.export: serialization failed for export {} account {}", exportId, accountId, e);
        } catch (RuntimeException e) {
            fail(export, "assembly_failed");
            log.error("auth.export: assembly failed for export {} account {}", exportId, accountId, e);
        }
    }

    private void fail(AccountExport export, String reason) {
        export.setStatus(AccountExport.Status.FAILED);
        export.setFailureReason(reason);
        export.setPayload(null);
        accountExportRepository.save(export);
        recordAfterCommit(Outcome.FAILURE);
    }

    /** A counter an operator alerts on must not claim an outcome for a row the commit could still lose. */
    private void recordAfterCommit(Outcome outcome) {
        TransactionCallbacks.afterCommit(() -> metrics.record(Job.EXPORT_GENERATION, outcome));
    }
}
