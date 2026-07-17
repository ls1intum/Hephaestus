package de.tum.cit.aet.hephaestus.core.audit;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.core.audit.spi.ConfigAuditEntityType;
import de.tum.cit.aet.hephaestus.core.audit.spi.ConfigAuditEntry;
import de.tum.cit.aet.hephaestus.core.audit.spi.ConfigAuditPort;
import de.tum.cit.aet.hephaestus.core.audit.spi.ConfigAuditSnapshot;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.User;
import de.tum.cit.aet.hephaestus.workspace.AbstractWorkspaceIntegrationTest;
import de.tum.cit.aet.hephaestus.workspace.AccountType;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/**
 * The retention sweep is the only sanctioned way a row ever leaves this table, and the one place a sign
 * error is catastrophic in both directions: {@code >} instead of {@code <} deletes the live trail and
 * keeps the dead one. The changelog parity test pins the retention <em>constant</em>; only this pins the
 * <em>behaviour</em>, against a real Postgres where {@code make_interval} and the DB-side {@code now()}
 * actually evaluate.
 */
class ConfigAuditRetentionJobIntegrationTest extends AbstractWorkspaceIntegrationTest {

    @Autowired
    private ConfigAuditPort configAudit;

    @Autowired
    private ConfigAuditEventRepository repository;

    @Autowired
    private EntityManager entityManager;

    record Snap(Integer cooldownMinutes) implements ConfigAuditSnapshot {}

    @Test
    @Transactional
    void agesOutRowsPastTheWindowAndKeepsTheRest() {
        Workspace workspace = setupWorkspace("audit-retention");
        Long stale = writeRow(workspace, 1);
        Long fresh = writeRow(workspace, 2);
        // occurred_at is written by the recorder, so age the row directly rather than mocking a clock.
        backdate(stale, ConfigAuditRetentionJob.RETENTION_DAYS + 1);
        backdate(fresh, ConfigAuditRetentionJob.RETENTION_DAYS - 1);

        // The sweep's SQL, not sweep() itself: @SchedulerLock needs the Liquibase-created `shedlock`
        // table, which the ddl-auto test tier does not have. This is where the logic lives — the job is a
        // two-line delegate, and ConfigAuditRetentionJobTest pins that it passes RETENTION_DAYS through.
        int deleted = repository.deleteOlderThan(ConfigAuditRetentionJob.RETENTION_DAYS);
        entityManager.clear();

        assertThat(deleted).isEqualTo(1);

        assertThat(repository.findById(stale)).as("a row past the retention window must age out").isEmpty();
        assertThat(repository.findById(fresh)).as("a row inside the window must survive").isPresent();
    }

    private Long writeRow(Workspace workspace, int cooldown) {
        configAudit.record(
            ConfigAuditEntry.updated(
                ConfigAuditEntityType.PRACTICE_REVIEW_SETTINGS,
                workspace.getId(),
                workspace.getId(),
                new Snap(cooldown),
                new Snap(cooldown + 100)
            )
        );
        entityManager.flush();
        return repository.findAll().stream().map(ConfigAuditEvent::getId).max(Long::compareTo).orElseThrow();
    }

    private void backdate(Long id, int days) {
        entityManager
            .createNativeQuery("UPDATE config_audit_event SET occurred_at = :ts WHERE id = :id")
            .setParameter("ts", Instant.now().minus(days, ChronoUnit.DAYS))
            .setParameter("id", id)
            .executeUpdate();
    }

    private Workspace setupWorkspace(String slug) {
        User owner = persistUser(slug + "-owner");
        Workspace workspace = createWorkspace(slug, "Audit Workspace", slug + "-org", AccountType.ORG, owner);
        ensureAdminMembership(workspace);
        return workspace;
    }
}
