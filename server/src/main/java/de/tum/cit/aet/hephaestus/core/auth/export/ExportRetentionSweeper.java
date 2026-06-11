package de.tum.cit.aet.hephaestus.core.auth.export;

import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import de.tum.cit.aet.hephaestus.core.runtime.ConditionalOnServerRole;
import java.time.Clock;
import java.time.Instant;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Hourly sweep that enforces the {@link ExportGenerationWorker#RETENTION 48h} retention window:
 * READY exports past {@code expires_at} flip to EXPIRED and have their payload nulled so the
 * exported PII isn't retained beyond the download window.
 *
 * <p>Scheduling is gated by {@code ServerSchedulingConfig} ({@code @EnableScheduling} only on the
 * server role), and wrapped in {@link SchedulerLock} so concurrent server pods don't both run the
 * sweep — the same pattern as {@code OAuthStateNonceCleanupJob}.
 */
@ConditionalOnServerRole
@Component
@WorkspaceAgnostic("Pruning account-scoped, workspace-agnostic export rows")
public class ExportRetentionSweeper {

    private static final Logger log = LoggerFactory.getLogger(ExportRetentionSweeper.class);

    private final AccountExportRepository accountExportRepository;
    private final Clock clock;

    public ExportRetentionSweeper(AccountExportRepository accountExportRepository, Clock clock) {
        this.accountExportRepository = accountExportRepository;
        this.clock = clock;
    }

    /** Runs hourly. {@code expireNow()} is also callable directly from tests. */
    @Scheduled(cron = "0 0 * * * *")
    @SchedulerLock(name = "account-export-retention-sweep", lockAtMostFor = "PT5M", lockAtLeastFor = "PT30S")
    public void sweep() {
        int expired = expireNow();
        if (expired > 0) {
            log.info("auth.export: expired {} READY export(s) past retention", expired);
        }
    }

    @Transactional
    public int expireNow() {
        // Single bulk UPDATE — flips status to EXPIRED and frees the payload without loading the
        // (large) BYTEA blobs into the persistence context.
        return accountExportRepository.expireReadyBefore(Instant.now(clock));
    }
}
