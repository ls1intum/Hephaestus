package de.tum.cit.aet.hephaestus.core.auth.export;

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
 * Runs the actual bundle assembly off the request thread.
 *
 * <p>Lives in a separate bean from {@code AccountExportService} so the {@link Async} boundary is
 * a real proxy hop (self-invocation would silently run inline) — the same separation
 * {@code AuthEventLogger}/{@code AuthEventWriter} use for their {@code REQUIRES_NEW} boundary.
 * The async executor is the application's {@code applicationTaskExecutor} (a bounded pool that
 * waits for in-flight DB work on shutdown — see {@code SpringAsyncConfig}); under
 * {@code spring.threads.virtual.enabled} each task still runs on a managed thread.
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

    public ExportGenerationWorker(
        AccountExportRepository accountExportRepository,
        ExportBundleAssembler assembler,
        ObjectMapper objectMapper,
        Clock clock
    ) {
        this.accountExportRepository = accountExportRepository;
        this.assembler = assembler;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    /**
     * Generate the bundle for {@code exportId} (owned by {@code accountId}) and persist the
     * outcome. PROCESSING → READY on success (payload + expiry set), → FAILED on any error. Never
     * throws to the caller (it's fire-and-forget); failures are recorded on the row.
     */
    @Async
    @Transactional
    public void generate(Long exportId, Long accountId) {
        AccountExport export = accountExportRepository.findByIdAndAccountId(exportId, accountId).orElse(null);
        if (export == null) {
            // Row vanished (e.g. account hard-deleted between request and pickup). Nothing to do.
            log.warn("auth.export: generation skipped, export {} for account {} not found", exportId, accountId);
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
    }
}
