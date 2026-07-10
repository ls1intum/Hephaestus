package de.tum.cit.aet.hephaestus.core.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;

@Tag("integration")
class DataAccessEventAppendOnlyLiquibaseTest {

    @SuppressWarnings("resource") // closed in @AfterAll
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16")
        .withDatabaseName("data_access_event_test")
        .withUsername("test")
        .withPassword("test");

    @BeforeAll
    static void startAndMigrate() throws Exception {
        POSTGRES.start();
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

    @AfterAll
    static void stop() {
        POSTGRES.stop();
    }

    @Test
    @DisplayName("UPDATE on a disclosure row is blocked by the append-only trigger")
    void updateIsBlocked() throws Exception {
        try (Connection c = newConnection()) {
            long id = insertEvent(c, 100L, 1L, 2L, "PRACTICE_REPORT");
            assertThatThrownBy(() -> {
                try (
                    PreparedStatement ps = c.prepareStatement(
                        "UPDATE data_access_event SET actor_user_id = 999 WHERE id = ?"
                    )
                ) {
                    ps.setLong(1, id);
                    ps.executeUpdate();
                }
            }).hasMessageContaining("append-only");
        }
    }

    @Test
    @DisplayName("UPDATE succeeds when the account-erasure redaction marker is set")
    void updateWithRedactionMarkerSucceeds() throws Exception {
        try (Connection c = newConnection()) {
            long id = insertEvent(c, 103L, 1L, 2L, "PRACTICE_REPORT");

            assertThatCode(() -> {
                c.setAutoCommit(false);
                try (Statement marker = c.createStatement()) {
                    marker.execute("SET LOCAL hephaestus.audit_redact = 'on'");
                }
                try (
                    PreparedStatement ps = c.prepareStatement(
                        "UPDATE data_access_event SET subject_user_id = NULL WHERE id = ?"
                    )
                ) {
                    ps.setLong(1, id);
                    ps.executeUpdate();
                }
                c.commit();
                c.setAutoCommit(true);
            }).doesNotThrowAnyException();
        }
    }

    @Test
    @DisplayName("an ordinary DELETE (no purge marker) is blocked by the append-only trigger")
    void deleteWithoutMarkerIsBlocked() throws Exception {
        try (Connection c = newConnection()) {
            long id = insertEvent(c, 101L, 1L, 2L, "PRACTICE_REPORT");
            assertThatThrownBy(() -> {
                try (PreparedStatement ps = c.prepareStatement("DELETE FROM data_access_event WHERE id = ?")) {
                    ps.setLong(1, id);
                    ps.executeUpdate();
                }
            }).hasMessageContaining("append-only");
        }
    }

    @Test
    @DisplayName("DELETE succeeds when the GDPR purge marker is set on the transaction (the purge path)")
    void deleteWithPurgeMarkerSucceeds() throws Exception {
        try (Connection c = newConnection()) {
            insertEvent(c, 102L, 1L, 2L, "PRACTICE_REPORT");
            insertEvent(c, 102L, 3L, null, "PRACTICE_ROSTER");

            assertThatCode(() -> {
                c.setAutoCommit(false);
                try (Statement marker = c.createStatement()) {
                    marker.execute("SET LOCAL hephaestus.audit_purge = 'on'");
                }
                try (
                    PreparedStatement ps = c.prepareStatement("DELETE FROM data_access_event WHERE workspace_id = ?")
                ) {
                    ps.setLong(1, 102L);
                    ps.executeUpdate();
                }
                c.commit();
                c.setAutoCommit(true);
            }).doesNotThrowAnyException();

            assertThat(countForWorkspace(c, 102L)).isZero();
        }
    }

    private static long insertEvent(Connection c, long workspaceId, long actorId, Long subjectId, String resourceType)
        throws Exception {
        try (
            PreparedStatement ps = c.prepareStatement(
                "INSERT INTO data_access_event (occurred_at, workspace_id, actor_user_id, subject_user_id, resource_type) " +
                    "VALUES (now(), ?, ?, ?, ?) RETURNING id"
            )
        ) {
            ps.setLong(1, workspaceId);
            ps.setLong(2, actorId);
            if (subjectId == null) {
                ps.setNull(3, java.sql.Types.BIGINT);
            } else {
                ps.setLong(3, subjectId);
            }
            ps.setString(4, resourceType);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    private static long countForWorkspace(Connection c, long workspaceId) throws Exception {
        try (
            PreparedStatement ps = c.prepareStatement("SELECT count(*) FROM data_access_event WHERE workspace_id = ?")
        ) {
            ps.setLong(1, workspaceId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    private static Connection newConnection() throws Exception {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }
}
