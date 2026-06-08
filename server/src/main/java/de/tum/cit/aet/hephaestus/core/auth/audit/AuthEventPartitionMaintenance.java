package de.tum.cit.aet.hephaestus.core.auth.audit;

import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Rolls the {@code auth_event} partition set forward via pg_partman. The partitioning itself is
 * defined declaratively by Liquibase (changeset {@code 1780825201546-18} calls
 * {@code partman.create_parent}); this bean only runs the periodic maintenance —
 * {@code partman.run_maintenance_proc()} — which create-aheads upcoming months and DROPs partitions
 * past the 12-month retention window configured in {@code partman.part_config}.
 *
 * <p>Replaces the bespoke {@code AuthEventPartitionManager} (see the ADR superseding 0017): the team
 * now ships pg_partman in the Postgres image, so partition management is a standard extension rather
 * than hand-rolled DDL. Like {@code ExportRetentionSweeper}, {@code @Scheduled} is enabled only on the
 * server role (via {@code ServerSchedulingConfig}) and {@code @SchedulerLock} ensures a single replica
 * runs it. Retention's {@code DROP TABLE} is DDL, so it is unaffected by the {@code auth_event}
 * immutability trigger (which only blocks row UPDATE/DELETE).
 */
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
