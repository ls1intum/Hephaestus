package de.tum.cit.aet.hephaestus.core.auth.export;

import de.tum.cit.aet.hephaestus.core.PrivacyJobMetrics;
import de.tum.cit.aet.hephaestus.core.PrivacyJobMetrics.Job;
import de.tum.cit.aet.hephaestus.core.PrivacyJobMetrics.Outcome;
import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import de.tum.cit.aet.hephaestus.core.runtime.ConditionalOnServerRole;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Hourly sweep that enforces the {@link ExportGenerationWorker#RETENTION} window: READY exports past
 * {@code expires_at} flip to EXPIRED and have their payload nulled.
 *
 * <p>The bulk {@code @Modifying} update needs an active transaction, which only a real proxy hop into
 * {@link AccountExportService} opens.
 */
@ConditionalOnServerRole
@Component
@WorkspaceAgnostic("Pruning account-scoped, workspace-agnostic export rows")
public class ExportRetentionSweeper {

    private static final Logger log = LoggerFactory.getLogger(ExportRetentionSweeper.class);

    private final AccountExportService accountExportService;
    private final PrivacyJobMetrics metrics;

    public ExportRetentionSweeper(AccountExportService accountExportService, PrivacyJobMetrics metrics) {
        this.accountExportService = accountExportService;
        this.metrics = metrics;
    }

    @Scheduled(cron = "0 0 * * * *")
    @SchedulerLock(name = "account-export-retention-sweep", lockAtMostFor = "PT5M", lockAtLeastFor = "PT30S")
    public void sweep() {
        try {
            int expired = accountExportService.expireRetention();
            metrics.record(Job.EXPORT_RETENTION, Outcome.SUCCESS);
            metrics.recordAffected(Job.EXPORT_RETENTION, expired);
            if (expired > 0) {
                log.info("auth.export: expired {} READY export(s) past retention", expired);
            }
        } catch (RuntimeException e) {
            metrics.record(Job.EXPORT_RETENTION, Outcome.FAILURE);
            throw e;
        }
    }
}
