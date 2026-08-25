package de.tum.cit.aet.hephaestus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.tum.cit.aet.hephaestus.testconfig.PostgreSQLTestContainer;
import de.tum.cit.aet.hephaestus.testconfig.PostgreSQLTestContainer.TestDatabase;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import liquibase.Contexts;
import liquibase.LabelExpression;
import liquibase.Liquibase;
import liquibase.changelog.ChangeSet;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("database")
class InstanceSilentModeLiquibaseTest {

    private static final String CHANGELOG = "1785739495153_changelog.xml";
    private static final String MASTER = "db/instance-silent-mode-test.xml";
    private static final Contexts CONTEXTS = new Contexts("prod");

    private static final TestDatabase DATABASE = PostgreSQLTestContainer.createDatabase(
        "hephaestus_silent_mode_migration"
    );

    @Test
    void shouldFailSafeWithoutOverwritingOperatorChoicesWhenUpgrading() throws Exception {
        createPreMigrationSchema();

        updateFollowup();
        assertThat(scalar("SELECT silent_mode_engaged::text FROM instance_settings WHERE id = 1")).isEqualTo("true");
        assertSchemaHardening();

        rollbackFollowup();
        execute("UPDATE instance_settings SET silent_mode_engaged = FALSE, silent_mode_changed_at = now()");
        updateFollowup();
        assertThat(scalar("SELECT silent_mode_engaged::text FROM instance_settings WHERE id = 1")).isEqualTo("false");

        rollbackFollowup();
        execute("UPDATE instance_settings SET silent_mode_engaged = TRUE, silent_mode_changed_at = now()");
        updateFollowup();
        assertThat(scalar("SELECT silent_mode_engaged::text FROM instance_settings WHERE id = 1")).isEqualTo("true");

        rollbackFollowup();
        execute("DELETE FROM instance_settings");
        updateFollowup();
        assertThat(scalar("SELECT silent_mode_engaged::text FROM instance_settings WHERE id = 1")).isEqualTo("true");
    }

    private static void assertSchemaHardening() throws SQLException {
        assertThat(
            scalar(
                "SELECT column_default FROM information_schema.columns " +
                    "WHERE table_name = 'instance_settings' AND column_name = 'silent_mode_engaged'"
            )
        ).contains("true");
        assertThat(
            scalar(
                "SELECT count(*)::text FROM information_schema.columns " +
                    "WHERE table_name = 'instance_settings' AND column_name = 'version' AND is_nullable = 'NO'"
            )
        ).isEqualTo("1");
        assertThatThrownBy(() ->
            execute("INSERT INTO instance_settings (id, silent_mode_engaged, version) VALUES (2, TRUE, 0)")
        ).isInstanceOf(SQLException.class);
    }

    private static void createPreMigrationSchema() throws SQLException {
        execute(
            "CREATE TABLE instance_settings (" +
                "id BIGINT PRIMARY KEY, " +
                "silent_mode_engaged BOOLEAN NOT NULL, " +
                "silent_mode_reason VARCHAR(500), " +
                "silent_mode_changed_at TIMESTAMP WITH TIME ZONE, " +
                "silent_mode_changed_by VARCHAR(255))"
        );
        execute("INSERT INTO instance_settings (id, silent_mode_engaged) VALUES (1, FALSE)");
    }

    private static void updateFollowup() throws Exception {
        try (Liquibase liquibase = liquibase()) {
            List<ChangeSet> pending = liquibase.listUnrunChangeSets(CONTEXTS, new LabelExpression());
            assertThat(pending)
                .hasSize(3)
                .allSatisfy(changeSet -> assertThat(changeSet.getFilePath()).endsWith(CHANGELOG));
            liquibase.update(pending.size(), CONTEXTS, new LabelExpression());
        }
    }

    private static void rollbackFollowup() throws Exception {
        try (Liquibase liquibase = liquibase()) {
            liquibase.rollback(3, "prod");
        }
    }

    private static Liquibase liquibase() throws Exception {
        var database = DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(connect()));
        return new Liquibase(MASTER, new ClassLoaderResourceAccessor(), database);
    }

    private static Connection connect() throws SQLException {
        return DriverManager.getConnection(DATABASE.jdbcUrl(), DATABASE.username(), DATABASE.password());
    }

    private static void execute(String sql) throws SQLException {
        try (Connection connection = connect(); Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private static @Nullable String scalar(String sql) throws SQLException {
        try (
            Connection connection = connect();
            Statement statement = connection.createStatement();
            ResultSet rows = statement.executeQuery(sql)
        ) {
            return rows.next() ? rows.getString(1) : null;
        }
    }
}
