package de.tum.cit.aet.hephaestus.core.auth.export;

import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import de.tum.cit.aet.hephaestus.core.runtime.ConditionalOnServerRole;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Hourly sweep that enforces the {@link ExportGenerationWorker#RETENTION 48h} retention window:
 * READY exports past {@code expires_at} flip to EXPIRED and have their payload nulled so the
 * exported PII isn't retained beyond the download window.
 *
 * <p>This bean owns only scheduling and cross-pod locking; the transactional work lives in
 * {@link AccountExportService#expireRetention()}. Delegating across a real proxy hop (rather than a
 * self-invoked method) is what lets that {@code @Transactional} take effect — the bulk
 * {@code @Modifying} UPDATE requires an active transaction.
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

    private final AccountExportService accountExportService;

    public ExportRetentionSweeper(AccountExportService accountExportService) {
        this.accountExportService = accountExportService;
    }

    @Scheduled(cron = "0 0 * * * *")
    @SchedulerLock(name = "account-export-retention-sweep", lockAtMostFor = "PT5M", lockAtLeastFor = "PT30S")
    public void sweep() {
        int expired = accountExportService.expireRetention();
        if (expired > 0) {
            log.info("auth.export: expired {} READY export(s) past retention", expired);
        }
    }
}
