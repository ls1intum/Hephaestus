package de.tum.cit.aet.hephaestus.integration.schema;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import de.tum.cit.aet.hephaestus.testconfig.PostgreSQLTestContainer;
import de.tum.cit.aet.hephaestus.testconfig.PostgreSQLTestContainer.TestDatabase;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

/** Exercises the ADR-0022 assessment backfill SQL against the migrated production schema. */
@Tag("database")
class ObservationAssessmentBackfillIntegrationTest {

    private static final TestDatabase DATABASE =
            PostgreSQLTestContainer.createMigratedDatabase("hephaestus_assessment_backfill");

    private final JdbcTemplate jdbcTemplate = new JdbcTemplate(
            new SingleConnectionDataSource(DATABASE.jdbcUrl(), DATABASE.username(), DATABASE.password(), true));

    /**
     * The four backfill UPDATE statements, copied VERBATIM from changeSet {@code 1781092589259-60}
     * in {@code db/changelog/1781092589259_changelog.xml}. Do not paraphrase: the whole point is to
     * assert the exact migration SQL.
     */
    private static final List<String> BACKFILL_SQL = List.of(
            "UPDATE observation o SET assessment = 'GOOD' "
                    + "FROM practice p WHERE o.practice_id = p.id AND o.presence = 'PRESENT' AND p.polarity = 'DESIRABLE'",
            "UPDATE observation o SET assessment = 'BAD' "
                    + "FROM practice p WHERE o.practice_id = p.id AND o.presence = 'ABSENT' AND p.polarity = 'DESIRABLE'",
            "UPDATE observation o SET assessment = 'BAD' "
                    + "FROM practice p WHERE o.practice_id = p.id AND o.presence = 'PRESENT' AND p.polarity = 'UNDESIRABLE'",
            "UPDATE observation o SET assessment = 'GOOD' "
                    + "FROM practice p WHERE o.practice_id = p.id AND o.presence = 'ABSENT' AND p.polarity = 'UNDESIRABLE'");

    @Test
    @DisplayName(
            "ADR-0022 backfill derives observation.assessment from practice.polarity × presence (NOT_APPLICABLE ⇒ NULL)")
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

        // Disable the post-migration constraints while seeding the pre-backfill state.
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
                Map.of("id", undesirablePracticeId, "slug", "backfill-undesirable", "polarity", "UNDESIRABLE"));

        // Six observations: every (polarity, presence) pair, all with assessment NULL pre-backfill.
        UUID desPresent = seedObservation(desirablePracticeId, "PRESENT");
        UUID desAbsent = seedObservation(desirablePracticeId, "ABSENT");
        UUID desNa = seedObservation(desirablePracticeId, "NOT_APPLICABLE");
        UUID undPresent = seedObservation(undesirablePracticeId, "PRESENT");
        UUID undAbsent = seedObservation(undesirablePracticeId, "ABSENT");
        UUID undNa = seedObservation(undesirablePracticeId, "NOT_APPLICABLE");

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

        assertThat(assessmentOf(desNa))
                .as("DESIRABLE + NOT_APPLICABLE ⇒ NULL (no valence)")
                .isNull();
        assertThat(assessmentOf(undNa))
                .as("UNDESIRABLE + NOT_APPLICABLE ⇒ NULL (no valence)")
                .isNull();

        // The coherence invariant the migration's CHECK (changeSet -61) enforces holds for every
        // seeded row: assessment IS NULL  <=>  presence = 'NOT_APPLICABLE'.
        Integer violations = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM observation " + "WHERE (presence = 'NOT_APPLICABLE') <> (assessment IS NULL) "
                        + "AND practice_id IN (?, ?)",
                Integer.class,
                desirablePracticeId,
                undesirablePracticeId);
        assertThat(violations)
                .as("assessment IS NULL iff presence = NOT_APPLICABLE for every backfilled row")
                .isZero();
    }

    /** Seeds one observation row for a practice + presence, assessment left NULL. Returns its id. */
    private UUID seedObservation(long practiceId, String presence) {
        UUID id = UUID.randomUUID();
        Map<String, Object> overrides = new LinkedHashMap<>();
        overrides.put("id", id);
        overrides.put("practice_id", practiceId);
        overrides.put("presence", presence);
        // artifact_kind carries a value-restricting CHECK (IN ('scm.pull_request','scm.issue')); the generic
        // dummy filler can't know that, so pin a valid value explicitly.
        overrides.put("artifact_kind", "scm.pull_request");
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
        List<Map<String, @Nullable Object>> columns = jdbcTemplate.queryForList(
                "SELECT column_name, data_type, is_nullable, column_default " + "FROM information_schema.columns "
                        + "WHERE table_schema = 'public' AND table_name = ?",
                table);

        Map<String, Object> values = new LinkedHashMap<>(overrides);
        Map<String, String> dataTypes = new HashMap<>();
        for (Map<String, Object> col : columns) {
            String name = (String) col.get("column_name");
            String dataType = (String) col.get("data_type");
            assertNotNull(name);
            assertNotNull(dataType);
            dataTypes.put(name, dataType);
            if (values.containsKey(name)) {
                continue;
            }
            boolean nullable = "YES".equals(col.get("is_nullable"));
            boolean hasDefault = col.get("column_default") != null;
            if (nullable || hasDefault) {
                continue; // leave to NULL / DB default
            }
            values.put(name, dummyFor(dataType, name));
        }

        String cols = String.join(", ", values.keySet());
        String placeholders = String.join(
                ", ",
                values.keySet().stream()
                        .map(name -> switch (required(dataTypes.get(name))) {
                            case "json" -> "?::json";
                            case "jsonb" -> "?::jsonb";
                            default -> "?";
                        })
                        .toList());
        jdbcTemplate.update(
                "INSERT INTO " + table + " (" + cols + ") VALUES (" + placeholders + ")",
                values.values().toArray());
    }

    /** Type-appropriate dummy for a NOT-NULL column the test does not otherwise care about. */
    private Object dummyFor(String dataType, String columnName) {
        return switch (dataType) {
            case "uuid" -> UUID.randomUUID();
            case "bigint", "integer", "smallint" -> 1L;
            case "real", "double precision", "numeric" -> 0.0;
            case "boolean" -> Boolean.FALSE;
            case "jsonb", "json" -> "{}";
            case "timestamp with time zone", "timestamp without time zone" ->
                java.sql.Timestamp.from(java.time.Instant.now());
            // character varying / text: keep it unique so any UNIQUE NOT-NULL column (e.g.
            // observation.occurrence_key) doesn't collide across the seeded rows.
            default -> "seed-" + columnName + "-" + UUID.randomUUID();
        };
    }

    private @Nullable String assessmentOf(UUID observationId) {
        return jdbcTemplate.queryForObject(
                "SELECT assessment FROM observation WHERE id = ?", String.class, observationId);
    }

    private static <T> T required(@org.jspecify.annotations.Nullable T value) {
        assertNotNull(value);
        return value;
    }

    private boolean columnExists(String table, String column) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM information_schema.columns "
                        + "WHERE table_schema = 'public' AND table_name = ? AND column_name = ?",
                Integer.class,
                table,
                column);
        return count != null && count > 0;
    }

    private boolean constraintExists(String constraintName) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM pg_constraint WHERE conname = ?", Integer.class, constraintName);
        return count != null && count > 0;
    }
}
