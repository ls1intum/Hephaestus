package de.tum.cit.aet.hephaestus.core.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.tum.cit.aet.hephaestus.core.audit.spi.ConfigAuditEntityType;
import de.tum.cit.aet.hephaestus.core.audit.spi.ConfigAuditEntry;
import de.tum.cit.aet.hephaestus.core.audit.spi.ConfigAuditPort;
import de.tum.cit.aet.hephaestus.core.audit.spi.ConfigAuditSnapshot;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.User;
import de.tum.cit.aet.hephaestus.workspace.AbstractWorkspaceIntegrationTest;
import de.tum.cit.aet.hephaestus.workspace.AccountType;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * Executes the append-only guarantee, which nothing else does.
 *
 * <p>Changeset {@code -2} is {@code context="prod"} and the test tier runs {@code ddl-auto: create} with
 * Liquibase off, so the triggers the feature is sold on — "entries can't be edited or removed" — ship
 * having never run. This installs that changeset's SQL against the real Postgres and attacks it.
 */
class ConfigAuditImmutabilityIntegrationTest extends AbstractWorkspaceIntegrationTest {

    private static final Path CHANGELOG = Path.of("src/main/resources/db/changelog/1784242879917_changelog.xml");

    @Autowired
    private ConfigAuditPort configAudit;

    @Autowired
    private JdbcTemplate jdbc;

    record Snap(Integer cooldownMinutes) implements ConfigAuditSnapshot {}

    /**
     * Installs changeset -2's forward SQL. Called explicitly rather than in a {@code @BeforeEach} so a
     * test can arrange rows (e.g. backdate one past the retention window) before the table becomes
     * immutable — the trigger blocks that arrangement too, which is the point of it.
     */
    private void installProdTriggers() {
        // Lifted from the changelog rather than restated here: a copy would drift, and this test would
        // then be guarding a trigger production does not have.
        jdbc.execute(immutabilitySql());
    }

    @Test
    @Transactional
    void editingARecordedChangeIsRejected() {
        long id = writeRow();
        installProdTriggers();

        assertThatThrownBy(() ->
            jdbc.update("UPDATE config_audit_event SET entity_type = ? WHERE id = ?", "AGENT_CONFIG", id)
        ).hasMessageContaining("append-only");
    }

    @Test
    @Transactional
    void erasingAnActorIsPermittedAndLeavesTheChangeItself() {
        long id = writeRow();
        installProdTriggers();

        assertThatCode(() -> jdbc.update("UPDATE config_audit_event SET actor_account_id = NULL WHERE id = ?", id))
            .as("GDPR Art. 17 erasure has to remain possible on an append-only table")
            .doesNotThrowAnyException();
        assertThat(jdbc.queryForObject("SELECT new_value::text FROM config_audit_event WHERE id = ?", String.class, id))
            .as("erasing the actor must not erase what was changed")
            .contains("cooldownMinutes");
    }

    @Test
    @Transactional
    void erasureCannotBeUsedAsCoverToEditTheChange() {
        long id = writeRow();
        installProdTriggers();

        // The carve-out is per-column: nulling a redactable column is allowed, but not as a rider on an
        // edit to a non-redactable one. This is the `to_jsonb(NEW) - redactable keys` comparison, the
        // most fragile expression in the changeset.
        assertThatThrownBy(() ->
            jdbc.update(
                "UPDATE config_audit_event SET actor_account_id = NULL, entity_type = ? WHERE id = ?",
                "AGENT_CONFIG",
                id
            )
        ).hasMessageContaining("append-only");
    }

    @Test
    @Transactional
    void deletingARowInsideTheRetentionWindowIsRejected() {
        long id = writeRow();
        installProdTriggers();

        assertThatThrownBy(() -> jdbc.update("DELETE FROM config_audit_event WHERE id = ?", id)).hasMessageContaining(
            "append-only"
        );
    }

    @Test
    @Transactional
    void deletingARowPastTheRetentionWindowIsPermitted() {
        long id = writeRow();
        // Backdated before the triggers exist: the trigger blocks editing occurred_at too. Separate test
        // from the rejection case because a raised trigger aborts the Postgres transaction, so nothing
        // after it in the same transaction can run.
        jdbc.update(
            "UPDATE config_audit_event SET occurred_at = now() - make_interval(days => ?) WHERE id = ?",
            ConfigAuditRetentionJob.RETENTION_DAYS + 1,
            id
        );
        installProdTriggers();

        assertThatCode(() -> jdbc.update("DELETE FROM config_audit_event WHERE id = ?", id))
            .as("the retention sweep must not be blocked by the immutability trigger")
            .doesNotThrowAnyException();
    }

    @Test
    @Transactional
    void truncatingTheTrailIsRejected() {
        writeRow();
        installProdTriggers();

        // Row-level triggers never fire on TRUNCATE, so without the statement-level trigger a single
        // statement erases the whole trail unopposed.
        assertThatThrownBy(() -> jdbc.execute("TRUNCATE config_audit_event")).hasMessageContaining("append-only");
    }

    private long writeRow() {
        User owner = persistUser("audit-immutability-owner");
        Workspace workspace = createWorkspace(
            "audit-immutability",
            "Audit Workspace",
            "audit-immutability-org",
            AccountType.ORG,
            owner
        );
        ensureAdminMembership(workspace);
        configAudit.record(
            ConfigAuditEntry.updated(
                ConfigAuditEntityType.PRACTICE_REVIEW_SETTINGS,
                workspace.getId(),
                workspace.getId(),
                new Snap(30),
                new Snap(10)
            )
        );
        Long id = jdbc.queryForObject("SELECT max(id) FROM config_audit_event", Long.class);
        assertThat(id).isNotNull();
        return id;
    }

    /** The `<sql>` body of changeset -2, without its `<rollback>`. */
    private static String immutabilitySql() {
        try {
            String changelog = Files.readString(CHANGELOG, StandardCharsets.UTF_8);
            Matcher changeset = Pattern.compile(
                "id=\"1784242879917-2-config-audit-event-immutability\".*?</changeSet>",
                Pattern.DOTALL
            ).matcher(changelog);
            assertThat(changeset.find()).as("immutability changeset present").isTrue();
            String body = changeset.group().replaceAll("(?s)<rollback>.*?</rollback>", "");
            Matcher sql = Pattern.compile("<!\\[CDATA\\[(.*?)]]>", Pattern.DOTALL).matcher(body);
            assertThat(sql.find()).as("immutability changeset carries SQL").isTrue();
            // The REVOKE is inert here (tests connect as a superuser) and irrelevant to what is asserted.
            return sql.group(1).replaceAll("(?m)^\\s*REVOKE[^;]*;", "");
        } catch (Exception e) {
            throw new AssertionError("could not read " + CHANGELOG.toAbsolutePath(), e);
        }
    }
}
