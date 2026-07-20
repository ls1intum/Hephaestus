package de.tum.cit.aet.hephaestus.core.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.tum.cit.aet.hephaestus.core.audit.spi.ConfigAuditAction;
import de.tum.cit.aet.hephaestus.core.audit.spi.ConfigAuditActorKind;
import de.tum.cit.aet.hephaestus.core.audit.spi.ConfigAuditEntityType;
import de.tum.cit.aet.hephaestus.testconfig.TestAsyncConfiguration;
import de.tum.cit.aet.hephaestus.testconfig.TestSecurityConfig;
import de.tum.cit.aet.hephaestus.workspace.AccountType;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import de.tum.cit.aet.hephaestus.workspace.WorkspaceRepository;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Attacks the append-only guarantee on a schema built by the real migration, under the {@code prod}
 * context that gates the immutability triggers. Every other tier uses {@code ddl-auto: create} with
 * Liquibase off, so these triggers otherwise ship having never run.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import({ TestSecurityConfig.class, TestAsyncConfiguration.class })
@Testcontainers
@Tag("integration")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ConfigAuditImmutabilityIntegrationTest {

    /** Dedicated and non-reused, so Liquibase always starts from an empty database. */
    @Container
    @SuppressWarnings("resource")
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16")
        .withDatabaseName("hephaestus_config_audit_immutability")
        .withUsername("test")
        .withPassword("test");

    @DynamicPropertySource
    static void applyProductionMigrations(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.liquibase.enabled", () -> "true");
        registry.add("spring.liquibase.change-log", () -> "classpath:db/master.xml");
        // The structural changesets carry no context and run regardless; `prod` is what adds the
        // immutability triggers this test attacks. Listing `dev` too matches how a dev instance boots,
        // so the schema under test is the one a developer actually gets.
        registry.add("spring.liquibase.contexts", () -> "dev,prod");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.datasource.hikari.maximum-pool-size", () -> "4");
        // Applying the full changeset set holds one connection past the 5s test-profile threshold.
        registry.add("spring.datasource.hikari.leak-detection-threshold", () -> "0");
    }

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private WorkspaceRepository workspaceRepository;

    @Autowired
    private ConfigAuditRetentionJob retentionJob;

    private long workspaceId;
    private long rowId;

    @BeforeEach
    void seedOneRecordedChange() {
        workspaceId = workspaceRepository
            .findByWorkspaceSlug(SLUG)
            .orElseGet(() -> {
                Workspace workspace = new Workspace();
                workspace.setWorkspaceSlug(SLUG);
                workspace.setDisplayName("Immutability");
                workspace.setAccountLogin(SLUG + "-org");
                workspace.setAccountType(AccountType.ORG);
                return workspaceRepository.save(workspace);
            })
            .getId();
        // No cleanup: the table under test refuses DELETE. Each test addresses its own row.
        rowId = insertRow(0);
    }

    @Test
    void editingARecordedChangeIsRejected() {
        assertThatThrownBy(() ->
            jdbc.update("UPDATE config_audit_event SET entity_type = ? WHERE id = ?", "AGENT_CONFIG", rowId)
        ).hasMessageContaining("append-only");
    }

    /**
     * Covers the database's half of Art. 17: the carve-out permits nulling an actor reference. That
     * {@code AccountPurger} issues this statement is not covered here — its account fixtures need the
     * auth module, which does not run Liquibase, so the trigger would be absent there.
     */
    @Test
    void erasingAnActorIsPermittedAndLeavesTheChangeItself() {
        assertThatCode(() -> jdbc.update("UPDATE config_audit_event SET actor_account_id = NULL WHERE id = ?", rowId))
            .as("GDPR Art. 17 erasure has to stay possible on an append-only table")
            .doesNotThrowAnyException();
        assertThat(
            jdbc.queryForObject("SELECT new_value::text FROM config_audit_event WHERE id = ?", String.class, rowId)
        )
            .as("erasing the actor must not erase what was changed")
            .contains("cooldownMinutes");
    }

    @Test
    void erasureCannotBeUsedAsCoverToEditTheChange() {
        // The carve-out is per-column, so nulling a redactable column may not ride along with an edit
        // to a non-redactable one. Exercises the `to_jsonb(NEW) - redactable keys` comparison.
        assertThatThrownBy(() ->
            jdbc.update(
                "UPDATE config_audit_event SET actor_account_id = NULL, entity_type = ? WHERE id = ?",
                "AGENT_CONFIG",
                rowId
            )
        ).hasMessageContaining("append-only");
    }

    @Test
    void deletingARowInsideTheRetentionWindowIsRejected() {
        assertThatThrownBy(() ->
            jdbc.update("DELETE FROM config_audit_event WHERE id = ?", rowId)
        ).hasMessageContaining("append-only");
    }

    @Test
    void deletingARowPastTheRetentionWindowIsPermitted() {
        long aged = insertRow(ConfigAuditRetentionJob.RETENTION_DAYS + 1);

        assertThatCode(() -> jdbc.update("DELETE FROM config_audit_event WHERE id = ?", aged))
            .as("the retention sweep must not be blocked by the immutability trigger")
            .doesNotThrowAnyException();
    }

    @Test
    void truncatingTheTrailIsRejected() {
        // Row-level triggers never fire on TRUNCATE; without a statement-level one a single statement
        // erases the whole trail unopposed.
        assertThatThrownBy(() -> jdbc.execute("TRUNCATE config_audit_event")).hasMessageContaining("append-only");
    }

    @Test
    void theSweepRemovesOnlyRowsPastTheWindow() {
        // sweep(), not the repository method: @SchedulerLock needs the Liquibase `shedlock` table, which
        // only exists on a migrated schema — so this is the one tier that can run the real entry point.
        // A row aged exactly RETENTION_DAYS is deliberately not asserted: the seed and the sweep each
        // call now() in their own transaction, so it is older than the cutoff by microseconds and the
        // case is a race, not a boundary.
        long stale = insertRow(ConfigAuditRetentionJob.RETENTION_DAYS + 1);
        long inside = insertRow(ConfigAuditRetentionJob.RETENTION_DAYS - 1);
        long fresh = insertRow(0);

        retentionJob.sweep();

        assertThat(rowExists(stale)).as("a row past the window ages out").isFalse();
        assertThat(rowExists(inside)).as("a row inside the window survives").isTrue();
        assertThat(rowExists(fresh)).as("a recent row survives").isTrue();
    }

    @Test
    void theEntityTypeConstraintAcceptsEveryValueTheApplicationCanEmit() {
        // From the applied schema, not the changelog text: a CHECK narrowed by a later changeset is
        // invisible to anything that greps XML, and rejects an INSERT for a value Java still emits.
        assertThat(checkConstraintValues("ck_config_audit_event_entity_type")).containsExactlyInAnyOrderElementsOf(
            names(ConfigAuditEntityType.values())
        );
    }

    @Test
    void theActionConstraintAcceptsEveryValueTheApplicationCanEmit() {
        assertThat(checkConstraintValues("ck_config_audit_event_action")).containsExactlyInAnyOrderElementsOf(
            names(ConfigAuditAction.values())
        );
    }

    @Test
    void theActorKindConstraintAcceptsEveryValueTheApplicationCanEmit() {
        assertThat(checkConstraintValues("ck_config_audit_event_actor_kind")).containsExactlyInAnyOrderElementsOf(
            names(ConfigAuditActorKind.values())
        );
    }

    private static final String SLUG = "audit-immutability";

    private boolean rowExists(long id) {
        Integer count = jdbc.queryForObject("SELECT count(*) FROM config_audit_event WHERE id = ?", Integer.class, id);
        return count != null && count > 0;
    }

    private long insertRow(int ageInDays) {
        return jdbc.queryForObject(
            """
            INSERT INTO config_audit_event
                (workspace_id, entity_type, entity_id, action, actor_kind, actor_account_id,
                 old_value, new_value, changed_keys, occurred_at)
            VALUES (?, 'PRACTICE_REVIEW_SETTINGS', '1', 'UPDATED', 'USER', NULL,
                    '{"cooldownMinutes":30}'::jsonb, '{"cooldownMinutes":10}'::jsonb,
                    ARRAY['cooldownMinutes'], now() - make_interval(days => ?))
            RETURNING id
            """,
            Long.class,
            workspaceId,
            ageInDays
        );
    }

    /** The values a CHECK constraint admits, read out of {@code pg_constraint} on the live schema. */
    private List<String> checkConstraintValues(String constraintName) {
        String definition = jdbc.queryForObject(
            "SELECT pg_get_constraintdef(oid) FROM pg_constraint WHERE conname = ?",
            String.class,
            constraintName
        );
        return Arrays.stream(definition.split("'"))
            .filter(part -> part.matches("[A-Z_]{2,}"))
            .distinct()
            .toList();
    }

    private static List<String> names(Enum<?>[] values) {
        return Arrays.stream(values).map(Enum::name).toList();
    }
}
