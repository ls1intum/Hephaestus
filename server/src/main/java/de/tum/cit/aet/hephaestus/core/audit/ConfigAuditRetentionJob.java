package de.tum.cit.aet.hephaestus.core.audit;

import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import de.tum.cit.aet.hephaestus.core.runtime.ConditionalOnServerRole;
import lombok.RequiredArgsConstructor;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ages out config audit rows past the retention window — the only sanctioned way a row leaves this
 * table, since the immutability trigger blocks DELETE inside the window.
 *
 * <p>{@link #RETENTION_DAYS} is passed into the DELETE, but the trigger's carve-out has to hardcode
 * the same interval — SQL cannot read a Java constant. {@code ConfigAuditChangelogParityTest} pins the
 * two together, because drift is silent in both directions: a shorter window here makes every sweep
 * raise against the trigger and retention dies unnoticed; a longer one over-retains while the docs
 * still claim the window.
 */
@ConditionalOnServerRole
@Component
@RequiredArgsConstructor
public class ConfigAuditRetentionJob {

    /** Matches {@code auth_event}'s 12-month window. */
    public static final int RETENTION_DAYS = 365;

    private static final Logger log = LoggerFactory.getLogger(ConfigAuditRetentionJob.class);

    private final ConfigAuditEventRepository repository;

    @Scheduled(cron = "0 20 0 * * *")
    @SchedulerLock(name = "config-audit-retention")
    @WorkspaceAgnostic("Retention ages out rows across every workspace; there is no single tenant to scope it to")
    @Transactional
    public void sweep() {
        int deleted = repository.deleteOlderThan(RETENTION_DAYS);
        if (deleted > 0) {
            log.info("config.audit: retention removed {} rows older than {} days", deleted, RETENTION_DAYS);
        }
    }
}
