package de.tum.cit.aet.hephaestus.core.audit.access;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.tum.cit.aet.hephaestus.core.audit.spi.DataAccessResourceType;
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
 * Attacks the append-only guarantee on the disclosure trail, on a schema built by the real migration under
 * the {@code prod} context that gates the trigger. Every other tier uses {@code ddl-auto: create} with
 * Liquibase off, so this trigger otherwise ships having never run.
 *
 * <p>The three carve-outs are the point: erasure must not double as an edit, retention must not reach inside
 * its window, and the purge must not be available without the marker.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import({ TestSecurityConfig.class, TestAsyncConfiguration.class })
@Testcontainers
@Tag("integration")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class DataAccessEventImmutabilityIntegrationTest {

    private static final String SLUG = "disclosure-immutability";

    /** Dedicated and non-reused, so Liquibase always starts from an empty database. */
    @Container
    @SuppressWarnings("resource")
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16")
        .withDatabaseName("hephaestus_data_access_immutability")
        .withUsername("test")
        .withPassword("test");

    @DynamicPropertySource
    static void applyProductionMigrations(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.liquibase.enabled", () -> "true");
        registry.add("spring.liquibase.change-log", () -> "classpath:db/master.xml");
        registry.add("spring.liquibase.contexts", () -> "dev,prod");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.datasource.hikari.maximum-pool-size", () -> "4");
        registry.add("spring.datasource.hikari.leak-detection-threshold", () -> "0");
    }

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private WorkspaceRepository workspaceRepository;

    @Autowired
    private DataAccessRetentionJob retentionJob;

    @Autowired
    private DataAccessAuditRecorder recorder;

    private long workspaceId;
    private long otherWorkspaceId;
    private long rowId;

    private long workspaceId(String slug) {
        return workspaceRepository
            .findByWorkspaceSlug(slug)
            .orElseGet(() -> {
                Workspace workspace = new Workspace();
                workspace.setWorkspaceSlug(slug);
                workspace.setDisplayName(slug);
                workspace.setAccountLogin(slug + "-org");
                workspace.setAccountType(AccountType.ORG);
                return workspaceRepository.save(workspace);
            })
            .getId();
    }

    @BeforeEach
    void seedOneDisclosure() {
        workspaceId = workspaceRepository
            .findByWorkspaceSlug(SLUG)
            .orElseGet(() -> {
                Workspace workspace = new Workspace();
                workspace.setWorkspaceSlug(SLUG);
                workspace.setDisplayName("Disclosure immutability");
                workspace.setAccountLogin(SLUG + "-org");
                workspace.setAccountType(AccountType.ORG);
                return workspaceRepository.save(workspace);
            })
            .getId();
        otherWorkspaceId = workspaceId(SLUG + "-other");
        // No cleanup between tests: the table under test refuses DELETE. Each test addresses its own row.
        rowId = insertRow(0);
    }

    @Test
    void rewritingADisclosureIsRejected() {
        assertThatThrownBy(() ->
            jdbc.update("UPDATE data_access_event SET resource_type = ? WHERE id = ?", "PRACTICE_ROSTER", rowId)
        ).hasMessageContaining("append-only");
    }

    @Test
    void erasingTheSubjectIsPermittedAndLeavesTheFactOfTheDisclosure() {
        assertThatCode(() -> jdbc.update("UPDATE data_access_event SET subject_user_id = NULL WHERE id = ?", rowId))
            .as("GDPR Art. 17 erasure has to stay possible on an append-only table")
            .doesNotThrowAnyException();
        assertThat(jdbc.queryForObject("SELECT resource_type FROM data_access_event WHERE id = ?", String.class, rowId))
            .as("erasing who it was about must not erase that a disclosure happened")
            .isEqualTo("PRACTICE_REPORT");
    }

    @Test
    void erasingBothPartiesInOneStatementIsPermitted() {
        // Account erasure clears both roles at once — the person may appear as viewer and as subject.
        assertThatCode(() ->
            jdbc.update("UPDATE data_access_event SET actor_user_id = NULL, subject_user_id = NULL WHERE id = ?", rowId)
        ).doesNotThrowAnyException();
    }

    @Test
    void erasureCannotBeUsedAsCoverToRewriteTheDisclosure() {
        // The carve-out is per column, so nulling a redactable one may not ride along with an edit to
        // another. Exercises the `to_jsonb(NEW) - redactable keys` comparison.
        assertThatThrownBy(() ->
            jdbc.update(
                "UPDATE data_access_event SET subject_user_id = NULL, workspace_id = ? WHERE id = ?",
                workspaceId + 1,
                rowId
            )
        ).hasMessageContaining("append-only");
    }

    @Test
    void reassigningTheActorToSomeoneElseIsRejected() {
        // Nulling is erasure; pointing the record at a different person is falsification.
        assertThatThrownBy(() ->
            jdbc.update("UPDATE data_access_event SET actor_user_id = 999 WHERE id = ?", rowId)
        ).hasMessageContaining("append-only");
    }

    @Test
    void deletingARowInsideTheRetentionWindowIsRejected() {
        assertThatThrownBy(() -> jdbc.update("DELETE FROM data_access_event WHERE id = ?", rowId)).hasMessageContaining(
            "append-only"
        );
    }

    @Test
    void deletingARowPastTheRetentionWindowIsPermitted() {
        long aged = insertRow(DataAccessRetentionJob.RETENTION_DAYS + 1);

        assertThatCode(() -> jdbc.update("DELETE FROM data_access_event WHERE id = ?", aged))
            .as("the retention sweep must not be blocked by the immutability trigger")
            .doesNotThrowAnyException();
    }

    @Test
    void truncatingTheTrailIsRejected() {
        // Row-level triggers never fire on TRUNCATE; without a statement-level one a single statement
        // erases the whole trail unopposed.
        assertThatThrownBy(() -> jdbc.execute("TRUNCATE data_access_event")).hasMessageContaining("append-only");
    }

    @Test
    void theSweepRemovesOnlyRowsPastTheWindow() {
        // sweep(), not the repository method: @SchedulerLock needs the Liquibase `shedlock` table, which
        // only exists on a migrated schema — so this is the one tier that can run the real entry point.
        long stale = insertRow(DataAccessRetentionJob.RETENTION_DAYS + 1);
        long inside = insertRow(DataAccessRetentionJob.RETENTION_DAYS - 1);
        long fresh = insertRow(0);

        retentionJob.sweep();

        assertThat(rowExists(stale)).as("a row past the window ages out").isFalse();
        assertThat(rowExists(inside)).as("a row inside the window survives").isTrue();
        assertThat(rowExists(fresh)).as("a recent row survives").isTrue();
    }

    @Test
    void theWorkspacePurgeErasesEvenFreshRowsAndNothingElseCan() {
        long fresh = insertRow(0);
        long otherTenant = insertRowIn(otherWorkspaceId, 0);

        // Same statement without the marker: refused. This is the whole reason the marker exists rather
        // than the DELETE being open.
        assertThatThrownBy(() ->
            jdbc.update("DELETE FROM data_access_event WHERE workspace_id = ?", workspaceId)
        ).hasMessageContaining("append-only");

        int erased = recorder.purgeWorkspace(workspaceId);

        assertThat(erased).isPositive();
        assertThat(rowExists(fresh))
            .as("an erasure request must not leave a record of who read the erased data")
            .isFalse();
    }

    @Test
    void theCheckConstraintAcceptsEveryResourceTypeTheApplicationCanEmit() {
        // Read from the applied schema, not the changelog text: a CHECK narrowed by a later changeset is
        // invisible to anything that greps XML, and rejects an INSERT for a value Java still emits.
        assertThat(checkConstraintValues("ck_data_access_event_resource_type")).containsExactlyInAnyOrderElementsOf(
            Arrays.stream(DataAccessResourceType.values()).map(Enum::name).toList()
        );
    }

    private boolean rowExists(long id) {
        Integer count = jdbc.queryForObject("SELECT count(*) FROM data_access_event WHERE id = ?", Integer.class, id);
        return count != null && count > 0;
    }

    private long insertRow(int ageInDays) {
        return insertRowIn(workspaceId, ageInDays);
    }

    private long insertRowIn(long targetWorkspaceId, int ageInDays) {
        return jdbc.queryForObject(
            """
            INSERT INTO data_access_event
                (workspace_id, actor_user_id, subject_user_id, resource_type, occurred_at)
            VALUES (?, 11, 22, 'PRACTICE_REPORT', now() - make_interval(days => ?))
            RETURNING id
            """,
            Long.class,
            targetWorkspaceId,
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
}
