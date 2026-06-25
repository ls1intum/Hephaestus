package de.tum.cit.aet.hephaestus.integration.schema;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.testconfig.TestAsyncConfiguration;
import de.tum.cit.aet.hephaestus.testconfig.TestSecurityConfig;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
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
 * Proves the ADR-0022 four-quadrant assessment backfill (changeSet {@code 1781092589259-60}) is
 * correct: {@code observation.assessment} is derived from {@code practice.polarity × presence} as
 *
 * <pre>
 *   polarity     presence          assessment
 *   ----------   ---------------   -----------
 *   DESIRABLE    PRESENT           GOOD
 *   DESIRABLE    ABSENT            BAD
 *   UNDESIRABLE  PRESENT           BAD
 *   UNDESIRABLE  ABSENT            GOOD
 *   (any)        NOT_APPLICABLE    NULL
 * </pre>
 *
 * <h2>Design</h2>
 *
 * <p>This is <b>DESIGN B</b> from the review item. The backfill in changeSet {@code -60} runs once,
 * at boot, against whatever {@code observation} rows already exist — and in a fresh Testcontainers
 * boot there are none, so the live boot exercises the SQL over an empty table. Stopping the
 * composite production changelog mid-stream at an exact changeset (DESIGN A) is fragile: the full
 * {@code master.xml} is 600+ changesets across 20 files, the transient {@code practice.polarity}
 * column defaults every row to {@code DESIRABLE} anyway (so an {@code UNDESIRABLE} case would still
 * have to be hand-set), and Liquibase's update-to-count semantics span the whole composite log.
 *
 * <p>Instead, this test boots the <b>full, real</b> production schema via Liquibase (so
 * {@code observation.assessment} exists, the two ADR-0022 CHECK constraints are present, and the
 * transient {@code practice.polarity} column is gone — exactly the post-migration state), then
 * <b>re-creates the backfill scenario faithfully</b>:
 *
 * <ol>
 *   <li>re-add a transient {@code practice.polarity} column (the valence source the migration
 *       used);
 *   <li>seed {@code practice} rows across {@code polarity ∈ {DESIRABLE, UNDESIRABLE}} and
 *       {@code observation} rows across {@code presence ∈ {PRESENT, ABSENT, NOT_APPLICABLE}} with
 *       {@code assessment} left NULL — i.e. the pre-backfill state;
 *   <li>run the <b>four UPDATE statements copied verbatim</b> from changeSet
 *       {@code 1781092589259-60};
 *   <li>assert the full quadrant matrix, including that {@code NOT_APPLICABLE} rows stay NULL.
 * </ol>
 *
 * <p>The SQL under assertion is byte-for-byte the migration SQL (see {@link #BACKFILL_SQL}); only
 * the data it runs over is hand-seeded. To seed without dragging in the {@code workspace} / {@code
 * user} / {@code agent_job} FK web and the evolving NOT-NULL surface of those tables, the seed runs
 * with {@code session_replication_role = replica} (FK/trigger enforcement off; the Testcontainers
 * postgres role is a superuser) and fills every other NOT-NULL column generically from
 * {@code information_schema}. The container is dedicated and discarded after the class, so the
 * transient column and the disabled constraints never leak.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import({ TestSecurityConfig.class, TestAsyncConfiguration.class })
@Testcontainers
@Tag("integration")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ObservationAssessmentBackfillIntegrationTest {

    /**
     * Fresh, dedicated container — deliberately NOT a shared/reused instance, so Liquibase always
     * builds the production schema from an empty database. Lifecycle is bound to this class.
     */
    @Container
    @SuppressWarnings("resource")
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16")
        .withDatabaseName("hephaestus_assessment_backfill")
        .withUsername("test")
        .withPassword("test");

    @DynamicPropertySource
    static void overrideForProductionBootContract(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);

        // Flip the test-profile defaults (liquibase off, ddl-auto:create) back to the production
        // boot contract so the REAL migration set builds the schema. @DynamicPropertySource wins.
        registry.add("spring.liquibase.enabled", () -> "true");
        registry.add("spring.liquibase.change-log", () -> "classpath:db/master.xml");
        registry.add("spring.liquibase.contexts", () -> "dev");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");

        registry.add("spring.datasource.hikari.maximum-pool-size", () -> "4");
        registry.add("spring.datasource.hikari.minimum-idle", () -> "1");
        registry.add("spring.datasource.hikari.leak-detection-threshold", () -> "0");
    }

    /**
     * The four backfill UPDATE statements, copied VERBATIM from changeSet {@code 1781092589259-60}
     * in {@code db/changelog/1781092589259_changelog.xml}. Do not paraphrase: the whole point is to
     * assert the exact migration SQL.
     */
    private static final List<String> BACKFILL_SQL = List.of(
        "UPDATE observation o SET assessment = 'GOOD' " +
            "FROM practice p WHERE o.practice_id = p.id AND o.presence = 'PRESENT' AND p.polarity = 'DESIRABLE'",
        "UPDATE observation o SET assessment = 'BAD' " +
            "FROM practice p WHERE o.practice_id = p.id AND o.presence = 'ABSENT' AND p.polarity = 'DESIRABLE'",
        "UPDATE observation o SET assessment = 'BAD' " +
            "FROM practice p WHERE o.practice_id = p.id AND o.presence = 'PRESENT' AND p.polarity = 'UNDESIRABLE'",
        "UPDATE observation o SET assessment = 'GOOD' " +
            "FROM practice p WHERE o.practice_id = p.id AND o.presence = 'ABSENT' AND p.polarity = 'UNDESIRABLE'"
    );

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName(
        "ADR-0022 backfill derives observation.assessment from practice.polarity × presence (NOT_APPLICABLE ⇒ NULL)"
    )
    void assessmentBackfillProducesTheFourQuadrantMatrix() {
        // Sanity: the full production schema is what we are exercising — assessment exists, the
        // coherence CHECK is present, and the transient polarity column is gone post-migration.
        assertThat(columnExists("observation", "assessment"))
            .as("observation.assessment must exist (added by changeSet 1781092589259-60)")
            .isTrue();
        assertThat(constraintExists("chk_observation_presence_assessment"))
            .as("coherence CHECK from changeSet 1781092589259-61 must be present")
            .isTrue();
        assertThat(columnExists("practice", "polarity"))
            .as("transient practice.polarity must be dropped post-migration (changeSet 1781092589259-65)")
            .isFalse();

        // --- Re-create the pre-backfill scenario --------------------------------------------------
        // The post-migration coherence CHECK forbids PRESENT/ABSENT rows with a NULL assessment, so
        // it must be off while we seed the pre-backfill state. Drop both ADR-0022 CHECKs; the
        // container is discarded after this class, so nothing leaks.
        jdbcTemplate.execute("ALTER TABLE observation DROP CONSTRAINT IF EXISTS chk_observation_presence_assessment");
        jdbcTemplate.execute("ALTER TABLE observation DROP CONSTRAINT IF EXISTS chk_observation_assessment");

        // Re-add the transient valence source the migration read from.
        jdbcTemplate.execute("ALTER TABLE practice ADD COLUMN polarity VARCHAR(16) NOT NULL DEFAULT 'DESIRABLE'");

        // Seed without dragging in the FK web (workspace/user/agent_job) by turning off FK/trigger
        // enforcement for the seed. The Testcontainers postgres role is a superuser.
        jdbcTemplate.execute("SET session_replication_role = 'replica'");

        long desirablePracticeId = 9_000_001L;
        long undesirablePracticeId = 9_000_002L;
        insertRow("practice", Map.of("id", desirablePracticeId, "slug", "backfill-desirable", "polarity", "DESIRABLE"));
        insertRow(
            "practice",
            Map.of("id", undesirablePracticeId, "slug", "backfill-undesirable", "polarity", "UNDESIRABLE")
        );

        // Six observations: every (polarity, presence) pair, all with assessment NULL pre-backfill.
        java.util.UUID desPresent = seedObservation(desirablePracticeId, "PRESENT");
        java.util.UUID desAbsent = seedObservation(desirablePracticeId, "ABSENT");
        java.util.UUID desNa = seedObservation(desirablePracticeId, "NOT_APPLICABLE");
        java.util.UUID undPresent = seedObservation(undesirablePracticeId, "PRESENT");
        java.util.UUID undAbsent = seedObservation(undesirablePracticeId, "ABSENT");
        java.util.UUID undNa = seedObservation(undesirablePracticeId, "NOT_APPLICABLE");

        jdbcTemplate.execute("SET session_replication_role = 'origin'");

        // Precondition: every seeded row starts with a NULL assessment.
        assertThat(assessmentOf(desPresent)).isNull();
        assertThat(assessmentOf(desAbsent)).isNull();
        assertThat(assessmentOf(desNa)).isNull();
        assertThat(assessmentOf(undPresent)).isNull();
        assertThat(assessmentOf(undAbsent)).isNull();
        assertThat(assessmentOf(undNa)).isNull();

        // --- Run the migration SQL verbatim -------------------------------------------------------
        BACKFILL_SQL.forEach(jdbcTemplate::execute);

        // --- Assert the full four-quadrant matrix + NOT_APPLICABLE ⇒ NULL -------------------------
        assertThat(assessmentOf(desPresent)).as("DESIRABLE + PRESENT ⇒ GOOD").isEqualTo("GOOD");
        assertThat(assessmentOf(desAbsent)).as("DESIRABLE + ABSENT ⇒ BAD").isEqualTo("BAD");
        assertThat(assessmentOf(undPresent)).as("UNDESIRABLE + PRESENT ⇒ BAD").isEqualTo("BAD");
        assertThat(assessmentOf(undAbsent)).as("UNDESIRABLE + ABSENT ⇒ GOOD").isEqualTo("GOOD");

        assertThat(assessmentOf(desNa)).as("DESIRABLE + NOT_APPLICABLE ⇒ NULL (no valence)").isNull();
        assertThat(assessmentOf(undNa)).as("UNDESIRABLE + NOT_APPLICABLE ⇒ NULL (no valence)").isNull();

        // The coherence invariant the migration's CHECK (changeSet -61) enforces holds for every
        // seeded row: assessment IS NULL  <=>  presence = 'NOT_APPLICABLE'.
        Integer violations = jdbcTemplate.queryForObject(
            "SELECT count(*) FROM observation " +
                "WHERE (presence = 'NOT_APPLICABLE') <> (assessment IS NULL) " +
                "AND practice_id IN (?, ?)",
            Integer.class,
            desirablePracticeId,
            undesirablePracticeId
        );
        assertThat(violations).as("assessment IS NULL iff presence = NOT_APPLICABLE for every backfilled row").isZero();
    }

    /** Seeds one observation row for a practice + presence, assessment left NULL. Returns its id. */
    private java.util.UUID seedObservation(long practiceId, String presence) {
        java.util.UUID id = java.util.UUID.randomUUID();
        Map<String, Object> overrides = new LinkedHashMap<>();
        overrides.put("id", id);
        overrides.put("practice_id", practiceId);
        overrides.put("presence", presence);
        // artifact_type carries a value-restricting CHECK (IN ('PULL_REQUEST','ISSUE')); the generic
        // dummy filler can't know that, so pin a valid value explicitly.
        overrides.put("artifact_type", "PULL_REQUEST");
        // assessment intentionally omitted -> NULL (the pre-backfill state).
        insertRow("observation", overrides);
        return id;
    }

    /**
     * Generic insert that satisfies the table's full NOT-NULL surface: every NOT-NULL column without
     * a default and not supplied in {@code overrides} is filled with a type-appropriate dummy. FK
     * targets are not seeded — this runs under {@code session_replication_role = replica}. Keeps the
     * seed resilient to schema evolution (new NOT-NULL columns won't break this test).
     */
    private void insertRow(String table, Map<String, ?> overrides) {
        List<Map<String, Object>> columns = jdbcTemplate.queryForList(
            "SELECT column_name, data_type, is_nullable, column_default " +
                "FROM information_schema.columns " +
                "WHERE table_schema = 'public' AND table_name = ?",
            table
        );

        Map<String, Object> values = new LinkedHashMap<>(overrides);
        for (Map<String, Object> col : columns) {
            String name = (String) col.get("column_name");
            if (values.containsKey(name)) {
                continue;
            }
            boolean nullable = "YES".equals(col.get("is_nullable"));
            boolean hasDefault = col.get("column_default") != null;
            if (nullable || hasDefault) {
                continue; // leave to NULL / DB default
            }
            values.put(name, dummyFor((String) col.get("data_type"), name));
        }

        String cols = String.join(", ", values.keySet());
        String placeholders = String.join(
            ", ",
            values
                .keySet()
                .stream()
                .map(k -> "?")
                .toList()
        );
        jdbcTemplate.update(
            "INSERT INTO " + table + " (" + cols + ") VALUES (" + placeholders + ")",
            values.values().toArray()
        );
    }

    /** Type-appropriate dummy for a NOT-NULL column the test does not otherwise care about. */
    private Object dummyFor(String dataType, String columnName) {
        return switch (dataType) {
            case "uuid" -> java.util.UUID.randomUUID();
            case "bigint", "integer", "smallint" -> 1L;
            case "real", "double precision", "numeric" -> 0.0;
            case "boolean" -> Boolean.FALSE;
            case "jsonb", "json" -> "{}";
            case "timestamp with time zone", "timestamp without time zone" -> java.sql.Timestamp.from(
                java.time.Instant.now()
            );
            // character varying / text: keep it unique so any UNIQUE NOT-NULL column (e.g.
            // observation.occurrence_key) doesn't collide across the seeded rows.
            default -> "seed-" + columnName + "-" + java.util.UUID.randomUUID();
        };
    }

    private String assessmentOf(java.util.UUID observationId) {
        return jdbcTemplate.queryForObject(
            "SELECT assessment FROM observation WHERE id = ?",
            String.class,
            observationId
        );
    }

    private boolean columnExists(String table, String column) {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT count(*) FROM information_schema.columns " +
                "WHERE table_schema = 'public' AND table_name = ? AND column_name = ?",
            Integer.class,
            table,
            column
        );
        return count != null && count > 0;
    }

    private boolean constraintExists(String constraintName) {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT count(*) FROM pg_constraint WHERE conname = ?",
            Integer.class,
            constraintName
        );
        return count != null && count > 0;
    }
}
