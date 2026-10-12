package de.tum.cit.aet.hephaestus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.tum.cit.aet.hephaestus.testconfig.PostgreSQLTestContainer;
import de.tum.cit.aet.hephaestus.testconfig.PostgreSQLTestContainer.TestDatabase;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import liquibase.Contexts;
import liquibase.LabelExpression;
import liquibase.Liquibase;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("database")
class ReviewQueryIndexesMigrationTest {
    private static final String CHANGELOG = "db/changelog/1791557968341_changelog.xml";

    @Test
    void shouldBuildBothIndexesOnPopulatedTablesAndSupportRollbackAndReapplication() throws Exception {
        TestDatabase database = populatedDatabase();
        update(database);
        assertThat(query(database, "SELECT indexdef FROM pg_indexes WHERE indexname = 'idx_agent_job_review_work'"))
                .singleElement()
                .asString()
                .contains("workspace_id, purpose, job_type", "metadata ->> 'pull_request_id'", "created_at, id");
        assertThat(query(database, "SELECT indexdef FROM pg_indexes WHERE indexname = 'idx_artifact_signal_job'"))
                .singleElement()
                .asString()
                .contains("(job_id)", "WHERE (job_id IS NOT NULL)");
        assertThat(
                        query(
                                database,
                                "SELECT indisvalid::text FROM pg_index WHERE indexrelid IN ('idx_agent_job_review_work'::regclass, 'idx_artifact_signal_job'::regclass)"))
                .containsExactly("true", "true");
        try (Liquibase liquibase = liquibase(database)) {
            liquibase.rollback(2, "prod");
        }
        assertThat(
                        query(
                                database,
                                "SELECT indexname FROM pg_indexes WHERE indexname IN ('idx_agent_job_review_work', 'idx_artifact_signal_job')"))
                .isEmpty();
        update(database);
        assertThat(query(
                        database,
                        "SELECT count(*)::text FROM agent_job UNION ALL SELECT count(*)::text FROM artifact_signal"))
                .containsExactly("1", "3");
    }

    @Test
    void shouldRefuseAnInterruptedConcurrentIndexAndResumeAfterExplicitCleanup() throws Exception {
        TestDatabase database = populatedDatabase();
        assertThatThrownBy(() -> execute(
                        database,
                        "CREATE UNIQUE INDEX CONCURRENTLY idx_artifact_signal_job ON artifact_signal(job_id)"))
                .isInstanceOf(SQLException.class);
        assertThat(query(
                        database,
                        "SELECT indisvalid::text FROM pg_index WHERE indexrelid = 'idx_artifact_signal_job'::regclass"))
                .containsExactly("false");
        assertThatThrownBy(() -> update(database)).hasStackTraceContaining("idx_artifact_signal_job is invalid");
        execute(database, "DROP INDEX CONCURRENTLY idx_artifact_signal_job");
        update(database);
        assertThat(query(
                        database,
                        "SELECT indisvalid::text FROM pg_index WHERE indexrelid = 'idx_artifact_signal_job'::regclass"))
                .containsExactly("true");
    }

    private static TestDatabase populatedDatabase() throws SQLException {
        TestDatabase database = PostgreSQLTestContainer.createDatabase(
                "review_indexes_" + UUID.randomUUID().toString().replace("-", ""));
        execute(
                database,
                "CREATE TABLE agent_job (workspace_id bigint, purpose text, job_type text, metadata jsonb, created_at timestamptz, id uuid); CREATE TABLE artifact_signal (job_id uuid)");
        execute(
                database,
                "INSERT INTO agent_job VALUES (1, 'PRACTICE_REVIEW', 'PULL_REQUEST_REVIEW', '{\"pull_request_id\":\"7\"}', now(), '00000000-0000-0000-0000-000000000001'); INSERT INTO artifact_signal VALUES ('00000000-0000-0000-0000-000000000001'), ('00000000-0000-0000-0000-000000000001'), (NULL)");
        return database;
    }

    private static void update(TestDatabase database) throws Exception {
        try (Liquibase liquibase = liquibase(database)) {
            liquibase.update(new Contexts("prod"), new LabelExpression());
        }
    }

    private static Liquibase liquibase(TestDatabase database) throws Exception {
        return new Liquibase(
                CHANGELOG,
                new ClassLoaderResourceAccessor(),
                DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(connect(database))));
    }

    private static Connection connect(TestDatabase database) throws SQLException {
        return DriverManager.getConnection(database.jdbcUrl(), database.username(), database.password());
    }

    private static void execute(TestDatabase database, String sql) throws SQLException {
        try (Connection connection = connect(database);
                Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private static List<String> query(TestDatabase database, String sql) throws SQLException {
        try (Connection connection = connect(database);
                Statement statement = connection.createStatement();
                var rows = statement.executeQuery(sql)) {
            List<String> values = new ArrayList<>();
            while (rows.next()) values.add(rows.getString(1));
            return values;
        }
    }
}
