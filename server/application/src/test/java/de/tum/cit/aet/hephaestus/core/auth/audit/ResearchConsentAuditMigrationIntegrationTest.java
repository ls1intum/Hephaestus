package de.tum.cit.aet.hephaestus.core.auth.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import de.tum.cit.aet.hephaestus.testconfig.PostgreSQLTestContainer;
import de.tum.cit.aet.hephaestus.testconfig.PostgreSQLTestContainer.TestDatabase;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("database")
class ResearchConsentAuditMigrationIntegrationTest {

    private static final TestDatabase DATABASE =
            PostgreSQLTestContainer.createMigratedDatabase("hephaestus_research_consent_audit");

    @Test
    void researchConsentRevokedIsAdmittedByTheWidenedCheckConstraint() throws Exception {
        assertThatCode(() -> {
                    try (Connection connection = connect();
                            Statement statement = connection.createStatement()) {
                        statement.executeUpdate("CREATE TABLE auth_event_research_test PARTITION OF auth_event "
                                + "FOR VALUES FROM ('2000-01-01') TO ('2000-02-01')");
                        statement.executeUpdate("INSERT INTO auth_event (occurred_at, event_type, result, details) "
                                + "VALUES ('2000-01-15', 'RESEARCH_CONSENT_REVOKED', 'SUCCESS', "
                                + "'{\"source\":\"SLACK_APP_HOME\",\"login\":\"octocat\"}'::jsonb)");
                    }
                })
                .doesNotThrowAnyException();

        try (Connection connection = connect();
                Statement statement = connection.createStatement()) {
            ResultSet rows = statement.executeQuery(
                    "SELECT count(*) FROM auth_event WHERE event_type = 'RESEARCH_CONSENT_REVOKED'");
            assertThat(rows.next()).isTrue();
            assertThat(rows.getInt(1)).isEqualTo(1);
        }
    }

    private static Connection connect() throws Exception {
        return DriverManager.getConnection(DATABASE.jdbcUrl(), DATABASE.username(), DATABASE.password());
    }
}
