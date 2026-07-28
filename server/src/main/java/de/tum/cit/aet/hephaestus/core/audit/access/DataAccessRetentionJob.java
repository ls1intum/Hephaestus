package de.tum.cit.aet.hephaestus.core.audit.access;

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
 * Ages out disclosure rows past the retention window — with the workspace purge, one of only two ways a row
 * leaves this table, since the immutability trigger blocks DELETE inside the window.
 *
 * <p>A disclosure trail without a retention window is not a stronger compliance posture, it is a weaker one:
 * indefinitely retained records of who read whose feedback are themselves personal data, and storage
 * limitation (GDPR Art. 5(1)(e)) applies to the audit trail as much as to what it audits.
 *
 * <p>{@link #RETENTION_DAYS} is passed into the DELETE, but the trigger's carve-out hardcodes the same
 * interval — SQL cannot read a Java constant. {@code DataAccessEventImmutabilityIntegrationTest} runs this
 * sweep against the migrated schema, so a trigger window longer than this constant — the direction that
 * kills the sweep — fails the build.
 */
@ConditionalOnServerRole
@Component
@RequiredArgsConstructor
public class DataAccessRetentionJob {

    /** Matches {@code auth_event} and {@code config_audit_event}'s 12-month window. */
    public static final int RETENTION_DAYS = 365;

    private static final Logger log = LoggerFactory.getLogger(DataAccessRetentionJob.class);

    private final DataAccessEventRepository repository;

    /** Ten minutes after the config-audit sweep, so the two never contend for the same nightly window. */
    @Scheduled(cron = "0 30 0 * * *")
    @SchedulerLock(name = "data-access-audit-retention")
    @WorkspaceAgnostic("Retention ages out rows across every workspace; there is no single tenant to scope it to")
    @Transactional
    public void sweep() {
        int deleted = repository.deleteOlderThan(RETENTION_DAYS);
        if (deleted > 0) {
            log.info("audit.access: retention removed {} rows older than {} days", deleted, RETENTION_DAYS);
        }
    }
}
