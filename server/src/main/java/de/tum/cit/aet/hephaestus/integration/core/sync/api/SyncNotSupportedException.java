package de.tum.cit.aet.hephaestus.integration.core.sync.api;

import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import java.io.Serial;

/**
 * Thrown when a manual sync/backfill is requested for a kind with no registered
 * {@code IntegrationSyncRunner} (or, for {@code BACKFILL}, one that doesn't support it) — the
 * graceful-degradation path for providers/runners that haven't landed yet (this PR ships the core
 * substrate; per-kind wiring is a follow-up).
 *
 * <p>Mapped to 409 by {@link SyncController}'s local exception handler — deliberately not
 * {@code @ResponseStatus} (see {@link de.tum.cit.aet.hephaestus.integration.core.sync.SyncStateConflictException}
 * for why a blanket global advice can silently out-rank that annotation).
 */
public class SyncNotSupportedException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public SyncNotSupportedException(IntegrationKind kind) {
        super("Manual sync is not supported for kind=" + kind);
    }
}
