package de.tum.cit.aet.hephaestus.core.auth.audit;

import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import de.tum.cit.aet.hephaestus.core.runtime.ConditionalOnServerRole;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Runs pg_partman maintenance for {@code auth_event} (create-ahead + 12-month-retention DROP per
 * {@code partman.part_config}). The partitioning is defined by Liquibase (changeset
 * {@code 1780825201546-18}); this only calls {@code run_maintenance_proc()}. Server-role
 * {@code @Scheduled} (via {@code ServerSchedulingConfig}) + ShedLock so one replica runs it. Replaces
 * the bespoke {@code AuthEventPartitionManager} (ADR 0018).
 */
@ConditionalOnServerRole
@Component
@WorkspaceAgnostic("auth_event is account/system-scoped; partition maintenance is global, not tenant data")
public class AuthEventPartitionMaintenance {

    private static final Logger log = LoggerFactory.getLogger(AuthEventPartitionMaintenance.class);

    private final JdbcTemplate jdbcTemplate;

    public AuthEventPartitionMaintenance(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** Daily; {@code run_maintenance_proc} is idempotent and cheap. */
    @Scheduled(cron = "0 10 0 * * *")
    @SchedulerLock(name = "auth-event-partition-maintenance", lockAtMostFor = "PT5M", lockAtLeastFor = "PT30S")
    public void maintain() {
        jdbcTemplate.execute("CALL partman.run_maintenance_proc()");
        log.debug("auth.audit: pg_partman run_maintenance_proc executed for auth_event");
    }
}
