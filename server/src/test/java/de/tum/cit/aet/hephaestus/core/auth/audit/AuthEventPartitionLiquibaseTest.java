package de.tum.cit.aet.hephaestus.core.auth.audit;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import liquibase.Contexts;
import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Proof test for the {@code auth_event} partitioning fix (the production-breaking bug this change
 * closes). The normal test profile DISABLES Liquibase and uses Hibernate {@code ddl-auto: create},
 * which emits a plain non-partitioned {@code auth_event} table — so it would never catch the bug.
 *
 * <p>This test instead spins up a FRESH stock {@code postgres:16} container with NO {@code
 * pg_partman} (only the bundled {@code citext}), runs the REAL Liquibase {@code db/master.xml}
 * against it, then performs a raw-JDBC {@code INSERT} into {@code auth_event} and reads it back.
 *
 * <p><b>Before the fix</b> the insert fails with {@code no partition of relation "auth_event" found
 * for row} (the parent had zero partitions because the {@code pg_partman} changeset MARK_RANs on
 * stock Postgres). <b>After the fix</b> the Liquibase bootstrap changeset has created the DEFAULT +
 * current-month partitions, so the insert succeeds and lands in the current-month partition.
 */
@Tag("integration")
class AuthEventPartitionLiquibaseTest {

    @SuppressWarnings("resource") // closed in @AfterAll
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16")
        .withDatabaseName("auth_event_liquibase_test")
        .withUsername("test")
        .withPassword("test");

    @BeforeAll
    static void startAndMigrate() throws Exception {
        POSTGRES.start();
        runLiquibaseMaster();
    }

    @AfterAll
    static void stop() {
        POSTGRES.stop();
    }

    /**
     * Runs the production changelog ({@code classpath:db/master.xml}) against the stock container —
     * exactly what Spring Boot would do on a real deployment, minus the application context.
     */
    private static void runLiquibaseMaster() throws Exception {
        // The classic Liquibase parser validates against the bundled dbchangelog XSD; some
        // long-standing attributes (e.g. deleteSetNull) trip strict offline validation in this
        // bare harness even though SpringLiquibase accepts them at real boot. We are proving
        // partition behaviour, not XSD conformance, so skip changelog XML validation here.
        System.setProperty("liquibase.validateXmlChangelogFiles", "false");
        try (Connection connection = newConnection()) {
            Database database = DatabaseFactory.getInstance().findCorrectDatabaseImplementation(
                new JdbcConnection(connection)
            );
            try (Liquibase liquibase = new Liquibase("db/master.xml", new ClassLoaderResourceAccessor(), database)) {
                liquibase.update(new Contexts());
            }
        }
    }

    @Test
    void authEventInsertSucceedsOnStockPostgresWithoutPgPartman() throws Exception {
        try (Connection connection = newConnection(); Statement statement = connection.createStatement()) {
            // The parent is partitioned (no pg_partman is installed on this stock image)...
            assertThat(isPartitioned(statement, "auth_event")).as("auth_event must be a partitioned parent").isTrue();
            assertThat(extensionInstalled(statement, "pg_partman"))
                .as("pg_partman must NOT be present — this proves we run on stock Postgres")
                .isFalse();

            // ...and the DEFAULT catch-all partition plus the current month exist after migration.
            assertThat(relationExists(statement, "auth_event_default")).isTrue();
            assertThat(currentMonthPartitionExists(statement))
                .as("Liquibase bootstrap must pre-create the current month's partition")
                .isTrue();

            // The actual bug repro: insert a row dated now() and read it back. Fails pre-fix with
            // "no partition of relation auth_event found for row".
            long id = nextAuthEventId(statement);
            String insert =
                "INSERT INTO auth_event (id, occurred_at, event_type, result, ip_inet) " +
                "VALUES (" +
                id +
                ", now(), 'LOGIN', 'SUCCESS', '203.0.113.7')";
            int inserted = statement.executeUpdate(insert);
            assertThat(inserted).isEqualTo(1);

            try (ResultSet rs = statement.executeQuery("SELECT event_type, result FROM auth_event WHERE id = " + id)) {
                assertThat(rs.next()).as("inserted auth_event row must be readable back").isTrue();
                assertThat(rs.getString("event_type")).isEqualTo("LOGIN");
                assertThat(rs.getString("result")).isEqualTo("SUCCESS");
            }

            // The row must have landed in the current month's partition, not the DEFAULT catch-all.
            assertThat(rowCount(statement, "auth_event_default"))
                .as("a current-dated row must NOT fall into the DEFAULT partition")
                .isZero();
        }
    }

    private static Connection newConnection() throws Exception {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    private static long nextAuthEventId(Statement statement) throws Exception {
        try (ResultSet rs = statement.executeQuery("SELECT nextval('auth_event_id_seq')")) {
            rs.next();
            return rs.getLong(1);
        }
    }

    private static boolean isPartitioned(Statement statement, String table) throws Exception {
        try (ResultSet rs = statement.executeQuery("SELECT relkind FROM pg_class WHERE relname = '" + table + "'")) {
            return rs.next() && "p".equals(rs.getString(1)); // 'p' = partitioned table
        }
    }

    private static boolean relationExists(Statement statement, String relation) throws Exception {
        try (ResultSet rs = statement.executeQuery("SELECT 1 FROM pg_class WHERE relname = '" + relation + "'")) {
            return rs.next();
        }
    }

    private static boolean currentMonthPartitionExists(Statement statement) throws Exception {
        try (
            ResultSet rs = statement.executeQuery(
                "SELECT 1 FROM pg_class WHERE relname = 'auth_event_p' || to_char(now(), 'YYYYMM')"
            )
        ) {
            return rs.next();
        }
    }

    private static boolean extensionInstalled(Statement statement, String extension) throws Exception {
        try (ResultSet rs = statement.executeQuery("SELECT 1 FROM pg_extension WHERE extname = '" + extension + "'")) {
            return rs.next();
        }
    }

    private static int rowCount(Statement statement, String table) throws Exception {
        try (ResultSet rs = statement.executeQuery("SELECT count(*) FROM " + table)) {
            rs.next();
            return rs.getInt(1);
        }
    }
}
