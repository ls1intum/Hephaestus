package de.tum.cit.aet.hephaestus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.tum.cit.aet.hephaestus.testconfig.PostgreSQLTestContainer;
import de.tum.cit.aet.hephaestus.testconfig.PostgreSQLTestContainer.TestDatabase;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import liquibase.Contexts;
import liquibase.Liquibase;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.exception.PreconditionFailedException;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Runs the practice script changelog against populated tables in their released shape. Only the columns, constraints,
 * functions and triggers that the changelog reads or changes are created.
 */
@Tag("database")
class PracticeScriptModelsMigrationTest {
    private static final String CHANGELOG = "db/changelog/1791486276211_changelog.xml";
    private static final String RELEASED_PROJECTION_CHANGELOG = "db/changelog/1790892486246_changelog.xml";
    private static final List<String> NEW_PROTOCOLS = List.of("openai-decisions", "openai-embeddings", "cohere-rerank");
    private static final List<String> NEW_SOURCE_TYPES =
            List.of("PRACTICE_SCRIPT_DECISION", "PRACTICE_SCRIPT_EMBEDDING", "PRACTICE_SCRIPT_RERANKING");

    @Test
    void shouldKeepEveryStoredConnectionAndAcceptTheNewProtocolsWhenMigrated() throws Exception {
        TestDatabase database = PostgreSQLTestContainer.createDatabase("llm_connection_protocol_migration");
        createReleasedSchema(database, true, true);
        for (String table : List.of("llm_connection", "workspace_llm_connection")) {
            execute(
                    database,
                    "INSERT INTO " + table + " (api_protocol) VALUES ('openai-completions'), ('openai-responses')");
        }

        migrate(database);

        for (String table : List.of("llm_connection", "workspace_llm_connection")) {
            assertThat(column(database, "SELECT api_protocol FROM " + table + " ORDER BY id"))
                    .containsExactly("openai-completions", "openai-responses");
            for (String protocol : NEW_PROTOCOLS) {
                execute(database, "INSERT INTO " + table + " (api_protocol) VALUES ('" + protocol + "')");
            }
            assertThatThrownBy(() ->
                            execute(database, "INSERT INTO " + table + " (api_protocol) VALUES ('anthropic-messages')"))
                    .isInstanceOf(SQLException.class);
        }
    }

    @Test
    void shouldKeepEveryLedgerRowAndAcceptThePracticeScriptSourcesWhenMigrated() throws Exception {
        TestDatabase database = PostgreSQLTestContainer.createDatabase("llm_usage_source_migration");
        createReleasedSchema(database, true, true);
        execute(database, """
                INSERT INTO llm_usage_event (id, source_type)
                VALUES ('00000000-0000-0000-0000-000000000001', 'AGENT_JOB'),
                       ('00000000-0000-0000-0000-000000000002', 'MENTOR_TURN')
                """);

        migrate(database);

        assertThat(column(database, "SELECT source_type FROM llm_usage_event ORDER BY id"))
                .containsExactly("AGENT_JOB", "MENTOR_TURN");
        // A row from before the upgrade billed no search units.
        assertThat(column(database, "SELECT search_units::text FROM llm_usage_event ORDER BY id"))
                .containsExactly("0", "0");
        for (String sourceType : NEW_SOURCE_TYPES) {
            execute(
                    database,
                    "INSERT INTO llm_usage_event (id, source_type) VALUES (gen_random_uuid(), '" + sourceType + "')");
        }
        assertThatThrownBy(
                        () -> execute(
                                database,
                                "INSERT INTO llm_usage_event (id, source_type) VALUES (gen_random_uuid(), 'PRACTICE_SCRIPT_CHAT')"))
                .isInstanceOf(SQLException.class);
    }

    @Test
    void shouldCascadePracticeScriptUsageWithItsJobAndRefuseAnUnknownKindWhenMigrated() throws Exception {
        TestDatabase database = PostgreSQLTestContainer.createDatabase("practice_script_usage_migration");
        createReleasedSchema(database, true, true);
        execute(database, "INSERT INTO workspace (id) VALUES (1)");
        execute(database, "INSERT INTO agent_job (id) VALUES ('00000000-0000-0000-0000-0000000000a1')");

        migrate(database);

        String insert = """
                INSERT INTO agent_job_practice_script_usage
                    (job_id, attempt, model_kind, practice_slug, workspace_id, calls, input_tokens, output_tokens)
                VALUES ('00000000-0000-0000-0000-0000000000a1', 0, '%s', 'comment-quality', 1, 1, 10, 2)
                """;
        execute(database, insert.formatted("DECISION"));
        assertThatThrownBy(() -> execute(database, insert.formatted("SPEECH"))).isInstanceOf(SQLException.class);
        execute(database, "DELETE FROM agent_job WHERE id = '00000000-0000-0000-0000-0000000000a1'");
        assertThat(column(
                        database,
                        "SELECT model_kind FROM agent_job_practice_script_usage "
                                + "WHERE job_id = '00000000-0000-0000-0000-0000000000a1'"))
                .isEmpty();
    }

    /**
     * A run goes with its job, keeps no revision that was deleted, and holds no models when the script ended
     * before it declared them.
     */
    @Test
    void shouldCascadeARunWithItsJobAndForgetADeletedRevisionWhenMigrated() throws Exception {
        TestDatabase database = PostgreSQLTestContainer.createDatabase("practice_script_run_migration");
        createReleasedSchema(database, true, true);
        execute(database, "INSERT INTO workspace (id) VALUES (1)");
        execute(database, "INSERT INTO practice_revision (id) VALUES (7)");
        execute(database, """
                INSERT INTO agent_job (id)
                VALUES ('00000000-0000-0000-0000-0000000000a1'), ('00000000-0000-0000-0000-0000000000a2')
                """);

        migrate(database);

        String insert = """
                INSERT INTO agent_job_practice_script_run
                    (job_id, attempt, practice_slug, workspace_id, practice_revision_id, status, leads, models,
                     finished_at)
                VALUES ('%s', 0, 'comment-quality', 1, 7, '%s', 0, %s, now())
                """;
        execute(database, insert.formatted("00000000-0000-0000-0000-0000000000a1", "OK", "'[]'"));
        execute(database, insert.formatted("00000000-0000-0000-0000-0000000000a2", "TIMED_OUT", "NULL"));
        assertThatThrownBy(() ->
                        execute(database, insert.formatted("00000000-0000-0000-0000-0000000000a2", "CRASHED", "NULL")))
                .isInstanceOf(SQLException.class);

        execute(database, "DELETE FROM practice_revision WHERE id = 7");
        assertThat(column(database, "SELECT practice_revision_id FROM agent_job_practice_script_run ORDER BY job_id"))
                .containsExactly(null, null);
        execute(database, "DELETE FROM agent_job WHERE id = '00000000-0000-0000-0000-0000000000a1'");
        assertThat(column(database, "SELECT status FROM agent_job_practice_script_run"))
                .containsExactly("TIMED_OUT");
    }

    @Test
    void shouldRenameTheReleasedScriptColumnsAndKeepThemCheckedWhenMigrated() throws Exception {
        TestDatabase database = PostgreSQLTestContainer.createDatabase("practice_script_column_migration");
        createReleasedSchema(database, true, true);
        execute(database, "INSERT INTO curated_practice_override (id, name, precompute_script) VALUES (1, 'A', 'x')");

        migrate(database);

        assertThat(column(database, """
                        SELECT table_name || '.' || column_name FROM information_schema.columns
                        WHERE table_schema = 'public' AND column_name IN ('precompute_script', 'practice_script')
                        ORDER BY 1
                        """))
                .containsExactly(
                        "curated_practice_override.practice_script",
                        "practice.practice_script",
                        "practice_revision.practice_script");
        assertThat(column(database, "SELECT practice_script FROM curated_practice_override WHERE id = 1"))
                .containsExactly("x");
        assertThat(column(database, """
                        SELECT pg_get_triggerdef(oid) FROM pg_trigger
                        WHERE tgname = 'practice_requires_current_revision_projection'
                        """))
                .singleElement()
                .asString()
                .contains("practice_script")
                .doesNotContain("precompute_script");
        assertThat(column(database, """
                        SELECT pg_get_constraintdef(oid) FROM pg_constraint
                        WHERE conname = 'ck_curated_practice_override_shape'
                        """))
                .singleElement()
                .asString()
                .contains("practice_script")
                .doesNotContain("precompute_script");
        assertThatThrownBy(() -> execute(
                        database, "INSERT INTO curated_practice_override (id, practice_script) VALUES (2, 'x')"))
                .isInstanceOf(SQLException.class);
    }

    /** The projection check reads the column by name when it runs, so a practice write proves its new body. */
    @Test
    void shouldStillWriteAPracticeAndRefuseADivergedScriptWhenMigrated() throws Exception {
        TestDatabase database = PostgreSQLTestContainer.createDatabase("practice_script_projection_migration");
        createReleasedSchema(database, true, true);

        migrate(database);

        try (Connection connection = connect(database);
                var statement = connection.createStatement()) {
            connection.setAutoCommit(false);
            statement.execute("INSERT INTO practice_group (id, slug) VALUES (1, 'quality')");
            statement.execute("""
                    INSERT INTO practice (id, practice_group_id, slug, name, applies_to, signals,
                        evidence_requirements, review_when, subject, criteria, practice_script,
                        automated_review_policy, delivery_behavior)
                    VALUES (1, 1, 'tests', 'Tests', 'scm.pull_request', '[]', '[]', '{}', 'AUTHOR', 'C',
                        'export default 1;', '{}', '{}')
                    """);
            statement.execute("""
                    INSERT INTO practice_revision (id, practice_id, slug, name, applies_to, signals,
                        evidence_requirements, review_when, subject, criteria, practice_script,
                        automated_review_policy, delivery_behavior, group_slug)
                    VALUES (1, 1, 'tests', 'Tests', 'scm.pull_request', '[]', '[]', '{}', 'AUTHOR', 'C',
                        'export default 1;', '{}', '{}', 'quality')
                    """);
            statement.execute("UPDATE practice SET current_revision_id = 1 WHERE id = 1");
            connection.commit();
        }
        assertThat(column(database, "SELECT practice_script FROM practice WHERE id = 1"))
                .containsExactly("export default 1;");

        assertThatThrownBy(() ->
                        execute(database, "UPDATE practice SET practice_script = 'export default 2;' WHERE id = 1"))
                .isInstanceOfSatisfying(
                        SQLException.class,
                        exception -> assertThat(exception.getSQLState()).isEqualTo("23514"));
    }

    /**
     * The rows hold no current revision, so the projection check would refuse any write to a column it watches.
     * The migration still succeeds, because the adoption base is not one of them.
     */
    @Test
    void shouldMoveTheScriptOfEveryStoredAdoptionBaseToItsNewKeyWhenMigrated() throws Exception {
        TestDatabase database = PostgreSQLTestContainer.createDatabase("practice_script_adopted_base_migration");
        createReleasedSchema(database, true, true);
        execute(database, "ALTER TABLE practice DISABLE TRIGGER practice_requires_current_revision_projection");
        for (String table : List.of("practice", "curated_practice_override")) {
            execute(database, """
                    INSERT INTO %s (id, name, adopted_base) VALUES
                        (11, 'With', '{"name": "With", "precomputeScript": "export default \\"\\u00e9\\";\\n", "criteria": "C", "signals": [{"value": "a"}]}'),
                        (12, 'Null', '{"name": "Null", "precomputeScript": null, "criteria": "C"}'),
                        (13, 'Absent', '{"name": "Absent", "criteria": "C"}'),
                        (14, 'None', NULL)
                    """.formatted(table));
        }
        execute(database, "ALTER TABLE practice ENABLE TRIGGER practice_requires_current_revision_projection");
        for (String table : List.of("practice", "curated_practice_override")) {
            assertThat(column(
                            database,
                            "SELECT (adopted_base -> 'precomputeScript')::text FROM %s WHERE id = 11".formatted(table)))
                    .containsExactly("\"export default \\\"é\\\";\\n\"");
        }

        migrate(database);

        for (String table : List.of("practice", "curated_practice_override")) {
            assertThat(column(database, "SELECT adopted_base::text FROM %s ORDER BY id".formatted(table)))
                    .containsExactly(
                            "{\"name\": \"With\", \"signals\": [{\"value\": \"a\"}], \"criteria\": \"C\", "
                                    + "\"practiceScript\": \"export default \\\"é\\\";\\n\"}",
                            "{\"name\": \"Null\", \"criteria\": \"C\", \"practiceScript\": null}",
                            "{\"name\": \"Absent\", \"criteria\": \"C\"}",
                            null);
        }
    }

    @Test
    void shouldHaltWhenTheConnectionConstraintToWidenIsMissing() throws Exception {
        TestDatabase database = PostgreSQLTestContainer.createDatabase("llm_connection_protocol_missing");
        createReleasedSchema(database, false, true);

        assertThatThrownBy(() -> migrate(database)).hasRootCauseInstanceOf(PreconditionFailedException.class);
    }

    @Test
    void shouldHaltWhenTheLedgerConstraintToWidenIsMissing() throws Exception {
        TestDatabase database = PostgreSQLTestContainer.createDatabase("llm_usage_source_missing");
        createReleasedSchema(database, true, false);

        assertThatThrownBy(() -> migrate(database)).hasRootCauseInstanceOf(PreconditionFailedException.class);
    }

    private static void createReleasedSchema(TestDatabase database, boolean protocolChecks, boolean sourceTypeCheck)
            throws Exception {
        for (String table : List.of("llm_connection", "workspace_llm_connection")) {
            String constraint = table.equals("llm_connection")
                    ? "ck_llm_connection_api_protocol"
                    : "ck_ws_llm_connection_api_protocol";
            String check = protocolChecks
                    ? ", CONSTRAINT " + constraint
                            + " CHECK (api_protocol IN ('openai-completions', 'openai-responses'))"
                    : "";
            execute(database, """
                    CREATE TABLE %s (
                        id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                        api_protocol varchar(40) NOT NULL%s
                    )
                    """.formatted(table, check));
        }
        execute(database, "CREATE TABLE workspace (id bigint PRIMARY KEY)");
        execute(database, "CREATE TABLE agent_job (id uuid PRIMARY KEY)");
        execute(database, "CREATE TABLE practice_revision (id bigint PRIMARY KEY)");
        String sourceCheck = sourceTypeCheck
                ? ", CONSTRAINT ck_llm_usage_event_source_type CHECK (source_type IN ('AGENT_JOB', 'MENTOR_TURN'))"
                : "";
        execute(database, """
                CREATE TABLE llm_usage_event (
                    id uuid PRIMARY KEY,
                    source_type varchar(20) NOT NULL%s
                )
                """.formatted(sourceCheck));
        createReleasedPracticeAggregate(database);
    }

    /** The practice tables, projection check, trigger and override shape check as v0.88.0 left them. */
    private static void createReleasedPracticeAggregate(TestDatabase database) throws Exception {
        execute(database, "CREATE TABLE practice_group (id bigint PRIMARY KEY, slug text NOT NULL)");
        execute(database, """
                CREATE TABLE practice (
                    id bigint PRIMARY KEY,
                    current_revision_id bigint,
                    practice_group_id bigint,
                    slug text, name text, applies_to text, signals jsonb, evidence_requirements jsonb,
                    review_when jsonb, subject text, precondition jsonb, criteria text, precompute_script text,
                    automated_review_policy jsonb, delivery_behavior jsonb, why_it_matters text,
                    what_good_looks_like text, adopted_base jsonb
                )
                """);
        execute(
                database,
                "ALTER TABLE practice_revision ADD COLUMN practice_id bigint, ADD COLUMN slug text, "
                        + "ADD COLUMN name text, ADD COLUMN applies_to text, ADD COLUMN signals jsonb, "
                        + "ADD COLUMN evidence_requirements jsonb, ADD COLUMN review_when jsonb, ADD COLUMN subject text, "
                        + "ADD COLUMN precondition jsonb, ADD COLUMN criteria text, ADD COLUMN precompute_script text, "
                        + "ADD COLUMN automated_review_policy jsonb, ADD COLUMN delivery_behavior jsonb, "
                        + "ADD COLUMN why_it_matters text, ADD COLUMN what_good_looks_like text, ADD COLUMN group_slug text");
        execute(database, """
                CREATE TABLE curated_practice_override (
                    id bigint PRIMARY KEY,
                    name text,
                    precompute_script text,
                    adopted_base jsonb,
                    CONSTRAINT ck_curated_practice_override_shape CHECK (
                        (name IS NULL AND precompute_script IS NULL) OR name IS NOT NULL)
                )
                """);
        execute(database, releasedProjectionFunction());
        execute(database, """
                CREATE CONSTRAINT TRIGGER practice_requires_current_revision_projection
                    AFTER INSERT OR UPDATE OF current_revision_id, slug, name, applies_to, signals, evidence_requirements,
                        review_when, subject, precondition, criteria, precompute_script, automated_review_policy,
                        delivery_behavior, why_it_matters, what_good_looks_like, practice_group_id ON practice
                    DEFERRABLE INITIALLY DEFERRED FOR EACH ROW
                    EXECUTE FUNCTION public.enforce_practice_current_revision_projection()
                """);
    }

    /** The projection check exactly as the released changelog defines it, so the test cannot drift from it. */
    private static String releasedProjectionFunction() throws Exception {
        String changelog;
        try (var stream = PracticeScriptModelsMigrationTest.class
                .getClassLoader()
                .getResourceAsStream(RELEASED_PROJECTION_CHANGELOG)) {
            assertThat(stream).as(RELEASED_PROJECTION_CHANGELOG).isNotNull();
            changelog = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
        int start =
                changelog.indexOf("CREATE OR REPLACE FUNCTION public.enforce_practice_current_revision_projection()");
        int end = changelog.indexOf("$$;", start) + "$$;".length();
        String function = changelog.substring(start, end);
        assertThat(function).contains("revision.precompute_script IS NOT DISTINCT FROM practice.precompute_script");
        return function;
    }

    private static List<@Nullable String> column(TestDatabase database, String query) throws SQLException {
        try (Connection connection = connect(database);
                var statement = connection.createStatement();
                var rows = statement.executeQuery(query)) {
            List<@Nullable String> values = new ArrayList<>();
            while (rows.next()) values.add(rows.getString(1));
            return values;
        }
    }

    private static Connection connect(TestDatabase database) throws SQLException {
        return DriverManager.getConnection(database.jdbcUrl(), database.username(), database.password());
    }

    private static void execute(TestDatabase database, String sql) throws SQLException {
        try (Connection connection = connect(database);
                var statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private static void migrate(TestDatabase database) throws Exception {
        var liquibaseDatabase =
                DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(connect(database)));
        try (Liquibase liquibase = new Liquibase(CHANGELOG, new ClassLoaderResourceAccessor(), liquibaseDatabase)) {
            liquibase.update(new Contexts());
        }
    }
}
