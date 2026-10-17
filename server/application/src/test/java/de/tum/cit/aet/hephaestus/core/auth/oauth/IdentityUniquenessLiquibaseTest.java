package de.tum.cit.aet.hephaestus.core.auth.oauth;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.tum.cit.aet.hephaestus.testconfig.PostgreSQLTestContainer;
import de.tum.cit.aet.hephaestus.testconfig.PostgreSQLTestContainer.TestDatabase;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import liquibase.Contexts;
import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

// The migrated index proves provider identity convergence independently of Hibernate schema creation.
@Tag("database")
class IdentityUniquenessLiquibaseTest {

    private static final String UNIQUE_INDEX = "uk_identity_provider_key";

    private static final TestDatabase DATABASE =
            PostgreSQLTestContainer.createMigratedDatabase("identity_uniqueness_test");

    @BeforeAll
    static void startAndMigrate() throws Exception {
        System.setProperty("liquibase.validateXmlChangelogFiles", "false");
        try (Connection connection = newConnection()) {
            Database database =
                    DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(connection));
            try (Liquibase liquibase = new Liquibase("db/master.xml", new ClassLoaderResourceAccessor(), database)) {
                liquibase.update(new Contexts("dev"));
            }
        }
    }

    @Test
    void shouldRejectARepeatedProviderKeyWithoutATeam() throws Exception {
        try (Connection c = newConnection()) {
            long provider = insertGitProvider(c, "GITHUB", "https://dup-null-team.example");
            long accountA = insertAccount(c, "A");
            long accountB = insertAccount(c, "B");

            // An empty team key identifies a provider login without a team.
            assertThatCode(() -> insertIdentity(c, accountA, provider, "race-subject", ""))
                    .doesNotThrowAnyException();

            assertThatThrownBy(() -> insertIdentity(c, accountB, provider, "race-subject", ""))
                    .hasMessageContaining(UNIQUE_INDEX);
        }
    }

    @Test
    void shouldAllowTheSameUserKeyOnDifferentProviders() throws Exception {
        try (Connection c = newConnection()) {
            long github = insertGitProvider(c, "GITHUB", "https://multi-provider-gh.example");
            long gitlab = insertGitProvider(c, "GITLAB", "https://multi-provider-gl.example");
            long account = insertAccount(c, "Multi");

            assertThatCode(() -> {
                        insertIdentity(c, account, github, "shared-subject", "");
                        insertIdentity(c, account, gitlab, "shared-subject", "");
                    })
                    .doesNotThrowAnyException();
        }
    }

    @Test
    void shouldPartitionProviderKeysByTeamAndRejectARepeatedTeam() throws Exception {
        try (Connection c = newConnection()) {
            long provider = insertGitProvider(c, "GITLAB", "https://teams.example.test");
            long a1 = insertAccount(c, "T1");
            long a2 = insertAccount(c, "T2");
            long a3 = insertAccount(c, "T3");

            // Distinct teams partition the provider key.
            assertThatCode(() -> {
                        insertIdentity(c, a1, provider, "team-subject", "team-alpha");
                        insertIdentity(c, a2, provider, "team-subject", "team-beta");
                    })
                    .doesNotThrowAnyException();

            // A repeated provider key within a team must collide.
            assertThatThrownBy(() -> insertIdentity(c, a3, provider, "team-subject", "team-alpha"))
                    .hasMessageContaining(UNIQUE_INDEX);
        }
    }

    private static long insertGitProvider(Connection c, String type, String serverUrl) throws Exception {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO identity_provider (type, server_url) VALUES (?, ?) RETURNING" + " id")) {
            ps.setString(1, type);
            ps.setString(2, serverUrl);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    private static long insertAccount(Connection c, String displayName) throws Exception {
        try (PreparedStatement ps = c.prepareStatement("INSERT INTO account (display_name) VALUES (?) RETURNING id")) {
            ps.setString(1, displayName);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    private static void insertIdentity(
            Connection c, long accountId, long providerId, String providerUserKey, String teamKey) throws Exception {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO identity (account_id, provider_id, provider_user_key, team_key, linked_via) VALUES (?, ?, ?, ?, 'SIGN_IN')")) {
            ps.setLong(1, accountId);
            ps.setLong(2, providerId);
            ps.setString(3, providerUserKey);
            ps.setString(4, teamKey);
            ps.executeUpdate();
        }
    }

    private static Connection newConnection() throws Exception {
        return DriverManager.getConnection(DATABASE.jdbcUrl(), DATABASE.username(), DATABASE.password());
    }
}
