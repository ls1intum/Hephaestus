package de.tum.cit.aet.hephaestus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.tum.cit.aet.hephaestus.testconfig.PostgreSQLTestContainer;
import de.tum.cit.aet.hephaestus.testconfig.PostgreSQLTestContainer.TestDatabase;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import liquibase.Contexts;
import liquibase.Liquibase;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("database")
class PracticeProjectionMigrationLiquibaseTest {

    private static final TestDatabase DATABASE =
            PostgreSQLTestContainer.createDatabase("practice_projection_migration_test");

    @Test
    void shouldRepairPracticeProjectionConstraintAcrossRollbackAndReapply() throws Exception {
        createBrokenPreMigrationSchema();
        update();
        assertThatCode(PracticeProjectionMigrationLiquibaseTest::seedMatchingProjection)
                .doesNotThrowAnyException();

        try (Liquibase liquibase = liquibase()) {
            liquibase.rollback(1, "");
        }
        assertThatThrownBy(() -> appendMatchingRevision(2, "After rollback"))
                .isInstanceOfSatisfying(
                        SQLException.class,
                        exception -> assertThat(exception.getSQLState()).isEqualTo("42P01"));

        update();
        assertThatCode(() -> appendMatchingRevision(2, "After repair")).doesNotThrowAnyException();

        assertThatThrownBy(() -> execute("UPDATE practice SET name = 'Diverged' WHERE id = 991003"))
                .isInstanceOfSatisfying(
                        SQLException.class,
                        exception -> assertThat(exception.getSQLState()).isEqualTo("23514"));
    }

    private static void createBrokenPreMigrationSchema() throws SQLException {
        execute("""
                CREATE TABLE practice_group (id bigint PRIMARY KEY, slug text NOT NULL)
                """, """
                CREATE TABLE practice (
                    id bigint PRIMARY KEY,
                    current_revision_id bigint,
                    practice_group_id bigint,
                    slug text NOT NULL,
                    name text NOT NULL,
                    applies_to text NOT NULL,
                    bindings jsonb NOT NULL,
                    criteria text NOT NULL,
                    precompute_script text,
                    automated_review_policy jsonb NOT NULL,
                    why_it_matters text,
                    what_good_looks_like text
                )
                """, """
                CREATE TABLE practice_revision (
                    id bigint PRIMARY KEY,
                    practice_id bigint NOT NULL,
                    revision_number integer NOT NULL,
                    slug text,
                    name text,
                    applies_to text,
                    bindings jsonb,
                    criteria text NOT NULL,
                    precompute_script text,
                    automated_review_policy jsonb,
                    why_it_matters text,
                    what_good_looks_like text,
                    group_slug text
                )
                """, """
                CREATE FUNCTION enforce_practice_current_revision_projection()
                RETURNS trigger
                LANGUAGE plpgsql
                AS $current_projection$
                BEGIN
                    PERFORM 1
                    FROM practice practice
                    LEFT JOIN practice_group practice_group
                      ON area.id = practice.practice_group_id
                    WHERE practice.id = NEW.id;
                    RETURN NULL;
                END;
                $current_projection$
                """, """
                CREATE CONSTRAINT TRIGGER practice_requires_current_revision_projection
                    AFTER INSERT OR UPDATE ON practice
                    DEFERRABLE INITIALLY DEFERRED
                    FOR EACH ROW EXECUTE FUNCTION enforce_practice_current_revision_projection()
                """);
    }

    private static void seedMatchingProjection() throws SQLException {
        try (Connection connection = connect();
                Statement statement = connection.createStatement()) {
            connection.setAutoCommit(false);
            statement.execute("INSERT INTO practice_group (id, slug) VALUES (991002, 'quality')");
            statement.execute("""
                INSERT INTO practice (
                    id, practice_group_id, slug, name, applies_to, bindings, criteria,
                    automated_review_policy
                ) VALUES (
                    991003, 991002, 'projection', 'Projection practice', 'scm.pull_request',
                    '[]'::jsonb, 'criteria', '{}'::jsonb
                )
                """);
            insertRevision(connection, 991004, 1, "Projection practice");
            statement.execute("UPDATE practice SET current_revision_id = 991004 WHERE id = 991003");
            connection.commit();
        }
    }

    private static void appendMatchingRevision(int revisionNumber, String name) throws SQLException {
        try (Connection connection = connect()) {
            connection.setAutoCommit(false);
            insertRevision(connection, 991005, revisionNumber, name);
            try (PreparedStatement update = connection.prepareStatement(
                    "UPDATE practice SET name = ?, current_revision_id = 991005 WHERE id = 991003")) {
                update.setString(1, name);
                update.executeUpdate();
            }
            connection.commit();
        }
    }

    private static void insertRevision(Connection connection, long id, int revisionNumber, String name)
            throws SQLException {
        try (PreparedStatement insert = connection.prepareStatement("""
            INSERT INTO practice_revision (
                id, practice_id, revision_number, slug, name, applies_to, bindings, criteria,
                automated_review_policy, group_slug
            ) VALUES (
                ?, 991003, ?, 'projection', ?, 'scm.pull_request', '[]'::jsonb, 'criteria',
                '{}'::jsonb, 'quality'
            )
            """)) {
            insert.setLong(1, id);
            insert.setInt(2, revisionNumber);
            insert.setString(3, name);
            insert.executeUpdate();
        }
    }

    private static void update() throws Exception {
        try (Liquibase liquibase = liquibase()) {
            liquibase.update(new Contexts());
        }
    }

    private static Liquibase liquibase() throws Exception {
        var database = DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(connect()));
        return new Liquibase("db/changelog/1788452647516_changelog.xml", new ClassLoaderResourceAccessor(), database);
    }

    private static Connection connect() throws SQLException {
        return DriverManager.getConnection(DATABASE.jdbcUrl(), DATABASE.username(), DATABASE.password());
    }

    private static void execute(String... statements) throws SQLException {
        try (Connection connection = connect();
                Statement statement = connection.createStatement()) {
            for (String sql : statements) {
                statement.execute(sql);
            }
        }
    }
}
