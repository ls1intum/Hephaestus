package de.tum.cit.aet.hephaestus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import liquibase.Contexts;
import liquibase.LabelExpression;
import liquibase.Liquibase;
import liquibase.changelog.ChangeSet;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@Tag("integration")
class InstanceSilentModeMigrationIntegrationTest {

    private static final String CHANGELOG = "1785739495153_changelog.xml";
    private static final String MASTER = "db/master.xml";
    private static final Contexts CONTEXTS = new Contexts("prod");

    @Container
    @SuppressWarnings("resource")
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16")
        .withDatabaseName("hephaestus_silent_mode_migration")
        .withUsername("test")
        .withPassword("test");

    @Test
    void upgradeIsFailSafeWithoutOverwritingOperatorChoices() throws Exception {
        updateUntilBeforeFollowup();

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

    private static void updateUntilBeforeFollowup() throws Exception {
        try (Liquibase liquibase = liquibase()) {
            List<ChangeSet> pending = liquibase.listUnrunChangeSets(CONTEXTS, new LabelExpression());
            List<Integer> indexes = indexesOf(pending);
            assertThat(indexes).isNotEmpty();
            liquibase.update(indexes.getFirst(), CONTEXTS, new LabelExpression());
        }
    }

    private static void updateFollowup() throws Exception {
        try (Liquibase liquibase = liquibase()) {
            List<ChangeSet> pending = liquibase.listUnrunChangeSets(CONTEXTS, new LabelExpression());
            List<Integer> indexes = indexesOf(pending);
            assertThat(indexes).hasSize(3).startsWith(0);
            liquibase.update(indexes.size(), CONTEXTS, new LabelExpression());
        }
    }

    private static void rollbackFollowup() throws Exception {
        try (Liquibase liquibase = liquibase()) {
            liquibase.rollback(3, "prod");
        }
    }

    private static List<Integer> indexesOf(List<ChangeSet> changeSets) {
        List<Integer> indexes = new ArrayList<>();
        for (int index = 0; index < changeSets.size(); index++) {
            if (changeSets.get(index).getFilePath().endsWith(CHANGELOG)) {
                indexes.add(index);
            }
        }
        return indexes;
    }

    private static Liquibase liquibase() throws Exception {
        var database = DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(connect()));
        return new Liquibase(MASTER, new ClassLoaderResourceAccessor(), database);
    }

    private static Connection connect() throws SQLException {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    private static void execute(String sql) throws SQLException {
        try (Connection connection = connect(); Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private static String scalar(String sql) throws SQLException {
        try (
            Connection connection = connect();
            Statement statement = connection.createStatement();
            ResultSet rows = statement.executeQuery(sql)
        ) {
            return rows.next() ? rows.getString(1) : null;
        }
    }
}
