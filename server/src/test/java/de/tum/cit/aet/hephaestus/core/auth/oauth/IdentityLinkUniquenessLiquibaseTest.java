package de.tum.cit.aet.hephaestus.core.auth.oauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Proves the {@code uq_identity_link_provider_subject_team} guarantee that the JIT first-login race
 * relies on, against the REAL Liquibase schema. The normal test profile uses Hibernate
 * {@code ddl-auto: create}, which emits a plain {@code UNIQUE(provider_id, subject, team_id)} —
 * and SQL treats {@code NULL} values as DISTINCT, so under ddl-auto two login identities with the
 * same {@code (provider, subject)} and a NULL {@code team_id} would NOT collide. Production instead
 * uses {@code CREATE UNIQUE INDEX ... (provider_id, subject, COALESCE(team_id, ''))}, which
 * collapses NULL teams to a single key.
 *
 * <p>That difference is load-bearing: it is the index violation that makes
 * {@link AccountProvisioningService}'s concurrent-first-login loser read-after-conflict and return
 * the winner instead of minting a duplicate account. A unit/ddl-auto test cannot see it, so this
 * raw-JDBC test runs the real {@code db/master.xml} on a stock {@code postgres:16} and asserts the
 * index semantics directly.
 */
@Tag("integration")
class IdentityLinkUniquenessLiquibaseTest {

    private static final String UNIQUE_INDEX = "uq_identity_link_provider_subject_team";

    @SuppressWarnings("resource") // closed in @AfterAll
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16")
        .withDatabaseName("identity_link_uniqueness_test")
        .withUsername("test")
        .withPassword("test");

    @BeforeAll
    static void startAndMigrate() throws Exception {
        POSTGRES.start();
        // The classic Liquibase parser validates against the bundled XSD; some long-standing
        // attributes trip strict offline validation in this bare harness though SpringLiquibase
        // accepts them at real boot. We are proving index behaviour, not XSD conformance.
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
    void duplicateProviderSubjectWithNullTeamIsRejected() throws Exception {
        try (Connection c = newConnection()) {
            long provider = insertGitProvider(c, "GITHUB", "https://dup-null-team.example");
            long accountA = insertAccount(c, "A");
            long accountB = insertAccount(c, "B");

            // First NULL-team login for (provider, subject) inserts cleanly.
            assertThatCode(() ->
                insertIdentityLink(c, accountA, provider, "race-subject", null)
            ).doesNotThrowAnyException();

            // Second NULL-team login for the SAME (provider, subject) on a DIFFERENT account must be
            // rejected by uq_identity_link_provider_subject_team — under ddl-auto's plain UNIQUE this
            // would WRONGLY succeed (NULL != NULL), duplicating the account. COALESCE(team_id,'') closes it.
            assertThatThrownBy(() ->
                insertIdentityLink(c, accountB, provider, "race-subject", null)
            ).hasMessageContaining(UNIQUE_INDEX);
        }
    }

    @Test
    void sameSubjectOnDifferentProvidersIsAllowed() throws Exception {
        try (Connection c = newConnection()) {
            long github = insertGitProvider(c, "GITHUB", "https://multi-provider-gh.example");
            long gitlab = insertGitProvider(c, "GITLAB", "https://multi-provider-gl.example");
            long account = insertAccount(c, "Multi");

            // The same subject string under two different providers are two distinct identities — the
            // provider is part of the key, so this must NOT collide. (Schema-level nOAuth separation.)
            assertThatCode(() -> {
                insertIdentityLink(c, account, github, "shared-subject", null);
                insertIdentityLink(c, account, gitlab, "shared-subject", null);
            }).doesNotThrowAnyException();
        }
    }

    @Test
    void sameProviderSubjectAcrossDistinctTeamsIsAllowedButRepeatedTeamCollides() throws Exception {
        try (Connection c = newConnection()) {
            long provider = insertGitProvider(c, "GITLAB", "https://teams.example.test");
            long a1 = insertAccount(c, "T1");
            long a2 = insertAccount(c, "T2");
            long a3 = insertAccount(c, "T3");

            // Distinct non-null teams partition the key (multi-instance IdP support).
            assertThatCode(() -> {
                insertIdentityLink(c, a1, provider, "team-subject", "team-alpha");
                insertIdentityLink(c, a2, provider, "team-subject", "team-beta");
            }).doesNotThrowAnyException();

            // Same provider+subject+team repeats the key → rejected, same as the NULL-team case.
            assertThatThrownBy(() ->
                insertIdentityLink(c, a3, provider, "team-subject", "team-alpha")
            ).hasMessageContaining(UNIQUE_INDEX);
        }
    }

    private static long insertGitProvider(Connection c, String type, String serverUrl) throws Exception {
        try (
            PreparedStatement ps = c.prepareStatement(
                "INSERT INTO identity_provider (type, server_url) VALUES (?, ?) RETURNING id"
            )
        ) {
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

    private static void insertIdentityLink(Connection c, long accountId, long providerId, String subject, String teamId)
        throws Exception {
        try (
            PreparedStatement ps = c.prepareStatement(
                "INSERT INTO identity_link (account_id, provider_id, subject, team_id) VALUES (?, ?, ?, ?)"
            )
        ) {
            ps.setLong(1, accountId);
            ps.setLong(2, providerId);
            ps.setString(3, subject);
            if (teamId == null) {
                ps.setNull(4, java.sql.Types.VARCHAR);
            } else {
                ps.setString(4, teamId);
            }
            ps.executeUpdate();
        }
    }

    private static Connection newConnection() throws Exception {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }
}
