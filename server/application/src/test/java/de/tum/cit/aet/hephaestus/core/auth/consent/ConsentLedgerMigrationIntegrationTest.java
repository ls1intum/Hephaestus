package de.tum.cit.aet.hephaestus.core.auth.consent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.tum.cit.aet.hephaestus.testconfig.PostgreSQLTestContainer;
import de.tum.cit.aet.hephaestus.testconfig.PostgreSQLTestContainer.TestDatabase;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HexFormat;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("database")
class ConsentLedgerMigrationIntegrationTest {

    private static final TestDatabase DATABASE =
            PostgreSQLTestContainer.createMigratedDatabase("consent_ledger_migration_test");

    @Test
    void archiveAndLedgerAreProtectedByTheDatabase() throws Exception {
        try (Connection connection = connect()) {
            ArchivedNotice notice = archivedNotice(connection);
            assertThat(sha256(notice.text())).isEqualTo(notice.sha256());

            long accountId = insertAccount(connection);
            long decisionId = insertDecision(connection, accountId, notice.version(), notice.sha256());

            assertThatThrownBy(() ->
                            execute(connection, "UPDATE consent_decision SET granted = false WHERE id = " + decisionId))
                    .hasMessageContaining("consent_decision is append-only");
            assertThatThrownBy(() -> execute(connection, "DELETE FROM consent_decision WHERE id = " + decisionId))
                    .hasMessageContaining("consent_decision is append-only");
            assertThatThrownBy(() -> insertDecision(connection, accountId, notice.version(), "0".repeat(64)))
                    .hasMessageContaining("fk_consent_decision_notice");
            assertThatThrownBy(() -> execute(
                            connection,
                            "UPDATE consent_notice SET notice_text = 'changed' WHERE version = '"
                                    + notice.version()
                                    + "'"))
                    .hasMessageContaining("consent_notice is immutable");

            assertThatCode(() -> execute(
                            connection, "UPDATE consent_decision SET account_id = NULL WHERE id = " + decisionId))
                    .doesNotThrowAnyException();
        }
    }

    private static ArchivedNotice archivedNotice(Connection connection) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT version, notice_text, sha256 FROM consent_notice WHERE version = ?")) {
            statement.setString(1, ConsentNotice.CURRENT_VERSION);
            try (ResultSet rows = statement.executeQuery()) {
                assertThat(rows.next()).isTrue();
                return new ArchivedNotice(rows.getString(1), rows.getString(2), rows.getString(3));
            }
        }
    }

    private static long insertAccount(Connection connection) throws Exception {
        try (Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery(
                        "INSERT INTO account (display_name) VALUES ('Consent test') RETURNING id")) {
            rows.next();
            return rows.getLong(1);
        }
    }

    private static long insertDecision(Connection connection, long accountId, String version, String sha256)
            throws Exception {
        try (PreparedStatement statement = connection.prepareStatement("INSERT INTO consent_decision "
                + "(account_id, purpose, granted, mechanism, notice_version, notice_sha256, occurred_at) "
                + "VALUES (?, 'RESEARCH_PARTICIPATION', true, 'FIRST_LOGIN_INTERSTITIAL', ?, ?, now()) "
                + "RETURNING id")) {
            statement.setLong(1, accountId);
            statement.setString(2, version);
            statement.setString(3, sha256);
            try (ResultSet rows = statement.executeQuery()) {
                rows.next();
                return rows.getLong(1);
            }
        }
    }

    private static void execute(Connection connection, String sql) throws Exception {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate(sql);
        }
    }

    private static String sha256(String text) throws Exception {
        return HexFormat.of()
                .formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
    }

    private static Connection connect() throws Exception {
        return DriverManager.getConnection(DATABASE.jdbcUrl(), DATABASE.username(), DATABASE.password());
    }

    private record ArchivedNotice(String version, String text, String sha256) {}
}
