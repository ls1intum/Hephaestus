package de.tum.cit.aet.hephaestus.integration.core.connection;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationState;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.User;
import de.tum.cit.aet.hephaestus.workspace.AbstractWorkspaceIntegrationTest;
import de.tum.cit.aet.hephaestus.workspace.AccountType;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * The admin disconnect endpoint documents a "best effort, proceed locally" contract: if the vendor
 * revoke fails, log it and still perform the local {@code UNINSTALLED} transition.
 *
 * <p>The revoke callback runs inside {@code ConnectionService#disconnect}, which holds a
 * {@code SELECT … FOR UPDATE} lifecycle lock on the connection row. If the callback reached erasers
 * ({@code ScmWorkspaceContentEraser} and friends) that are {@code @Transactional} with default
 * {@code REQUIRED} propagation, they JOINED that transaction — so a {@code DataAccessException} during
 * erasure marked the lifecycle transaction rollback-only, and the commit then threw {@code
 * UnexpectedRollbackException}: a 500 with the connection still ACTIVE, even though the log claimed it
 * had proceeded.
 *
 * <p>These tests run against real PostgreSQL because this is a transaction-boundary defect: mocks
 * cannot express "this connection's transaction is aborted". {@link FailingEraser} reproduces it — a
 * {@code @Transactional} REQUIRED bean that genuinely aborts its PostgreSQL transaction — rather than
 * merely throwing a Java exception.
 */
@Import(ConnectionDisconnectRevokeIsolationIntegrationTest.RevokeStubConfig.class)
class ConnectionDisconnectRevokeIsolationIntegrationTest extends AbstractWorkspaceIntegrationTest {

    @Autowired
    private ConnectionService connectionService;

    @Autowired
    private ConnectionRepository connectionRepository;

    @Autowired
    private ConnectionAuditRepository connectionAuditRepository;

    @Autowired
    private FailingEraser failingEraser;

    @Autowired
    private SucceedingEraser succeedingEraser;

    private Workspace workspace;
    private Connection connection;

    @BeforeEach
    void setUpConnection() {
        succeedingEraser.reset();
        User owner = persistUser("revoke-isolation-owner-" + System.nanoTime());
        workspace = createWorkspace(
            "revoke-isolation-ws-" + System.nanoTime(),
            "Revoke Isolation Test",
            "revoke-isolation-org",
            AccountType.ORG,
            owner
        );
        connection = connectionRepository.save(
            new Connection(
                workspace,
                IntegrationKind.GITHUB,
                "300",
                new ConnectionConfig.GitHubAppConfig(300L, "revoke-isolation-org", null, Set.of())
            )
        );
        connection.setState(IntegrationState.ACTIVE);
        connection = connectionRepository.save(connection);
    }

    @Test
    void revokeFailingWithDataAccessException_stillCommitsTheLocalUninstalledTransition() {
        Connection result = connectionService.disconnect(connection, disconnectRequest("corr-erase-fails"), () ->
            failingEraser.erase()
        );

        // No exception escaped: the endpoint returns 200, not 500.
        assertThat(result.getState()).isEqualTo(IntegrationState.UNINSTALLED);

        // Re-read on a fresh transaction to prove it COMMITTED, not merely mutated in memory: the audit
        // INSERT and this UPDATE would otherwise have run on the same JDBC connection the eraser aborted,
        // so PostgreSQL would reject them and the commit would unwind everything.
        Connection reloaded = connectionRepository.findById(connection.getId()).orElseThrow();
        assertThat(reloaded.getState()).isEqualTo(IntegrationState.UNINSTALLED);
        assertThat(reloaded.getCredentialsEncrypted()).isNull();
        // Read through the workspace-scoped query, not findAll(): connection_audit is a
        // workspace-scoped table, so an unscoped read is rejected by the tenancy statement inspector.
        assertThat(connectionAuditRepository.findByWorkspaceId(workspace.getId()))
            .as("the disconnect is auditable even though the erase failed")
            .anySatisfy(audit -> assertThat(audit.getCorrelationId()).isEqualTo("corr-erase-fails"));
    }

    /** The happy path must be untouched: the revoke runs, and the transition still commits. */
    @Test
    void revokeSucceeding_runsTheRevokeAndCommitsTheUninstalledTransition() {
        Connection result = connectionService.disconnect(connection, disconnectRequest("corr-erase-ok"), () ->
            succeedingEraser.erase()
        );

        assertThat(result.getState()).isEqualTo(IntegrationState.UNINSTALLED);
        assertThat(succeedingEraser.calls()).as("the revoke callback still runs exactly once").isEqualTo(1);

        Connection reloaded = connectionRepository.findById(connection.getId()).orElseThrow();
        assertThat(reloaded.getState()).isEqualTo(IntegrationState.UNINSTALLED);
        assertThat(connectionAuditRepository.findByWorkspaceId(workspace.getId())).anySatisfy(audit ->
            assertThat(audit.getCorrelationId()).isEqualTo("corr-erase-ok")
        );
    }

    private ConnectionService.TransitionRequest disconnectRequest(String correlationId) {
        return new ConnectionService.TransitionRequest(
            IntegrationState.UNINSTALLED,
            "DISCONNECT",
            "ADMIN",
            "test-admin",
            correlationId,
            "disconnect"
        );
    }

    @TestConfiguration
    static class RevokeStubConfig {

        @Bean
        FailingEraser failingEraser(JdbcTemplate jdbcTemplate) {
            return new FailingEraser(jdbcTemplate);
        }

        @Bean
        SucceedingEraser succeedingEraser(JdbcTemplate jdbcTemplate) {
            return new SucceedingEraser(jdbcTemplate);
        }
    }

    /**
     * Stands in for {@code ScmWorkspaceContentEraser} and friends: {@code @Transactional} with the
     * default {@code REQUIRED} propagation, so it joins whatever transaction the revoke callback runs
     * in. The division by zero is a genuine PostgreSQL error, which leaves that transaction ABORTED —
     * the state a mocked exception cannot reproduce.
     *
     * <p>It deliberately touches no table with a foreign key to {@code connection}: an INSERT into such
     * a table would take a {@code FOR KEY SHARE} lock on the row the caller holds {@code FOR UPDATE},
     * and — now that the callback runs on a second pooled connection — would self-deadlock instead of
     * failing. That constraint is documented on {@code ConnectionService#runRevokeIsolated}.
     */
    static class FailingEraser {

        private final JdbcTemplate jdbcTemplate;

        FailingEraser(JdbcTemplate jdbcTemplate) {
            this.jdbcTemplate = jdbcTemplate;
        }

        @Transactional
        public void erase() {
            jdbcTemplate.queryForObject("SELECT 1 / 0", Integer.class);
        }
    }

    /** The happy-path counterpart: same propagation, a real query, no failure. */
    static class SucceedingEraser {

        private final JdbcTemplate jdbcTemplate;
        private final AtomicInteger calls = new AtomicInteger();

        SucceedingEraser(JdbcTemplate jdbcTemplate) {
            this.jdbcTemplate = jdbcTemplate;
        }

        @Transactional
        public void erase() {
            jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            calls.incrementAndGet();
        }

        int calls() {
            return calls.get();
        }

        void reset() {
            calls.set(0);
        }
    }
}
