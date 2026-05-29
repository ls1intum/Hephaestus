package de.tum.cit.aet.hephaestus.core.auth.export;

import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import de.tum.cit.aet.hephaestus.core.auth.audit.AuthEvent;
import de.tum.cit.aet.hephaestus.core.auth.audit.AuthEventLogger;
import de.tum.cit.aet.hephaestus.core.auth.export.dto.ExportStatusDTO;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * GDPR Art. 20 self-service data export. Owns the {@link AccountExport} lifecycle and the
 * ownership checks behind {@code /user/exports/**}; the controller stays a thin HTTP adapter
 * (no repository access — enforced by {@code ArchitectureTest.controllersDoNotAccessRepositories}).
 *
 * <h2>Ownership / enumeration defense</h2>
 * Every read is scoped by {@code (id, accountId)}. A caller asking for another account's export
 * id gets {@link Optional#empty()}, which the controller maps to 404 (never 403) so an attacker
 * cannot distinguish "exists but not yours" from "doesn't exist".
 *
 * <h2>Async generation</h2>
 * {@link #requestExport} persists a PENDING row in the request transaction, then — after commit —
 * hands off to {@link ExportGenerationWorker#generate} on the application async executor. The
 * request returns 202 immediately; the client polls {@link #status}. The post-commit handoff
 * guarantees the worker (a new transaction) sees the committed row.
 */
@Service
@WorkspaceAgnostic("GDPR exports are account-scoped, spanning a principal's data across workspaces")
public class AccountExportService {

    private final AccountExportRepository accountExportRepository;
    private final ExportGenerationWorker generationWorker;
    private final AuthEventLogger authEventLogger;
    private final Clock clock;

    public AccountExportService(
        AccountExportRepository accountExportRepository,
        ExportGenerationWorker generationWorker,
        AuthEventLogger authEventLogger,
        Clock clock
    ) {
        this.accountExportRepository = accountExportRepository;
        this.generationWorker = generationWorker;
        this.authEventLogger = authEventLogger;
        this.clock = clock;
    }

    /** Create a PENDING export for the account and kick off async generation. */
    @Transactional
    public AccountExport requestExport(Long accountId) {
        AccountExport export = accountExportRepository.save(new AccountExport(accountId));
        authEventLogger
            .event(AuthEvent.EventType.EXPORT_REQUESTED, AuthEvent.Result.SUCCESS)
            .account(accountId)
            .details("{\"exportId\":" + export.getId() + "}")
            .record();
        // Hand off after the PENDING row commits so the worker's own transaction can read it.
        // The worker is @Async on a separate bean → real proxy hop (no inline self-invocation).
        Long exportId = export.getId();
        registerAfterCommit(() -> generationWorker.generate(exportId, accountId));
        return export;
    }

    /** Ownership-scoped status view; empty if the id doesn't exist or isn't this account's. */
    @Transactional(readOnly = true)
    public Optional<ExportStatusDTO> status(Long exportId, Long accountId) {
        return accountExportRepository.findByIdAndAccountId(exportId, accountId).map(this::toStatus);
    }

    /**
     * Ownership-scoped payload fetch for download. Returns the JSON bytes only when the export is
     * READY and not past its expiry; empty otherwise (missing, not-yours, not-ready, or expired)
     * so the controller answers 404 uniformly.
     */
    @Transactional(readOnly = true)
    public Optional<byte[]> downloadPayload(Long exportId, Long accountId) {
        return accountExportRepository
            .findByIdAndAccountId(exportId, accountId)
            .filter(e -> e.getStatus() == AccountExport.Status.READY)
            .filter(e -> e.getExpiresAt() == null || e.getExpiresAt().isAfter(Instant.now(clock)))
            .map(AccountExport::getPayload)
            .filter(payload -> payload != null && payload.length > 0);
    }

    private ExportStatusDTO toStatus(AccountExport e) {
        return new ExportStatusDTO(
            e.getId(),
            e.getStatus().name(),
            e.getRequestedAt(),
            e.getCompletedAt(),
            e.getExpiresAt()
        );
    }

    /**
     * Run {@code action} after the current transaction commits, falling back to inline execution
     * when there is no active synchronization (e.g. in a unit test without a real transaction).
     * Package-private + overridable so tests can assert the handoff without an executor.
     */
    void registerAfterCommit(Runnable action) {
        if (org.springframework.transaction.support.TransactionSynchronizationManager.isSynchronizationActive()) {
            org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                new org.springframework.transaction.support.TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        action.run();
                    }
                }
            );
        } else {
            action.run();
        }
    }
}
