package de.tum.cit.aet.hephaestus.integration.core.sync;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.tum.cit.aet.hephaestus.integration.core.connection.Connection;
import de.tum.cit.aet.hephaestus.integration.core.connection.ConnectionConfig;
import de.tum.cit.aet.hephaestus.integration.core.connection.ConnectionRepository;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationState;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.User;
import de.tum.cit.aet.hephaestus.workspace.AbstractWorkspaceIntegrationTest;
import de.tum.cit.aet.hephaestus.workspace.AccountType;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Real-Postgres coverage for the {@code ux_sync_job_active} partial UNIQUE index — the race-proof
 * backstop for the one-active-job-per-connection guard.
 *
 * <p><strong>Why this test creates the index by hand.</strong> The integration test substrate boots
 * with {@code spring.jpa.hibernate.ddl-auto=create} and Liquibase disabled (see
 * {@code application-test.yml}), and Hibernate's schema export cannot emit a <em>partial</em> unique
 * index ({@code WHERE status IN (...)}). So the changelog's {@code ux_sync_job_active} index does NOT
 * exist in the auto-generated schema — the DB-layer invariant would otherwise be exercised only
 * against a real Liquibase-migrated database ({@code mvn liquibase:update} / live E2E), matching the
 * {@code agent_job} precedent documented on the changeset. Here we recreate exactly that index DDL
 * against the Testcontainer Postgres so the constraint — and {@link SyncJobService}'s translation of
 * its violation into a {@link SyncJobConflictException} (not a 500) — is proven end-to-end.
 */
class SyncJobActiveIndexIntegrationTest extends AbstractWorkspaceIntegrationTest {

    private static final String CREATE_PARTIAL_INDEX =
        "CREATE UNIQUE INDEX IF NOT EXISTS ux_sync_job_active " +
        "ON sync_job (connection_id) WHERE status IN ('PENDING', 'RUNNING')";

    @Autowired
    private SyncJobService syncJobService;

    @Autowired
    private SyncJobRepository syncJobRepository;

    @Autowired
    private ConnectionRepository connectionRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Workspace workspace;
    private Connection connection;

    @BeforeEach
    void setUp() {
        // Recreate the Liquibase-only partial index against the real Postgres container.
        jdbcTemplate.execute(CREATE_PARTIAL_INDEX);

        User owner = persistUser("sync-index-owner-" + System.nanoTime());
        workspace = createWorkspace(
            "sync-index-ws-" + System.nanoTime(),
            "Sync Active Index Test",
            "sync-index-org",
            AccountType.ORG,
            owner
        );
        connection = connectionRepository.save(
            new Connection(
                workspace,
                IntegrationKind.GITHUB,
                "200",
                new ConnectionConfig.GitHubAppConfig(200L, "sync-index-org", null, Set.of())
            )
        );
        connection.setState(IntegrationState.ACTIVE);
        connection = connectionRepository.save(connection);
    }

    @AfterEach
    void dropIndex() {
        // Hygiene: this test owns the index; drop it so it can't leak into a context-sharing sibling.
        jdbcTemplate.execute("DROP INDEX IF EXISTS ux_sync_job_active");
    }

    @Test
    void partialIndex_rejectsSecondActiveRowForSameConnection_atDbLevel() {
        syncJobRepository.saveAndFlush(
            new SyncJob(workspace, connection, IntegrationKind.GITHUB, SyncJobType.INITIAL, SyncJobTrigger.MANUAL, null)
        );

        // A second PENDING row for the same connection violates the partial UNIQUE index.
        assertThatThrownBy(() ->
            syncJobRepository.saveAndFlush(
                new SyncJob(
                    workspace,
                    connection,
                    IntegrationKind.GITHUB,
                    SyncJobType.RECONCILIATION,
                    SyncJobTrigger.MANUAL,
                    null
                )
            )
        ).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void concurrentBeginJob_exactlyOneWins_theOtherGetsConflictNotIntegrityError() throws Exception {
        int threads = 2;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CyclicBarrier barrier = new CyclicBarrier(threads);
        AtomicInteger successes = new AtomicInteger();
        AtomicInteger conflicts = new AtomicInteger();
        List<Throwable> unexpected = new CopyOnWriteArrayList<>();

        try {
            List<Future<?>> futures = new java.util.ArrayList<>();
            for (int i = 0; i < threads; i++) {
                futures.add(
                    pool.submit(() -> {
                        try {
                            barrier.await(5, TimeUnit.SECONDS);
                            syncJobService.beginJob(
                                new SyncJobRequest(
                                    workspace.getId(),
                                    connection.getId(),
                                    IntegrationKind.GITHUB,
                                    SyncJobType.RECONCILIATION,
                                    SyncJobTrigger.MANUAL,
                                    null
                                )
                            );
                            successes.incrementAndGet();
                        } catch (SyncJobConflictException e) {
                            // The one-active guard denied the loser — either the service-level pre-check
                            // saw the winner's committed row, or the partial-index DataIntegrityViolation
                            // was translated here. Both are the correct 409-absorb outcome, never a 500.
                            conflicts.incrementAndGet();
                        } catch (Throwable t) {
                            unexpected.add(t);
                        }
                    })
                );
            }
            for (Future<?> f : futures) {
                f.get(10, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }

        assertThat(unexpected)
            .as("no DataIntegrityViolationException or other 500-class error should escape beginJob")
            .isEmpty();
        assertThat(successes.get()).as("exactly one concurrent trigger created a job").isEqualTo(1);
        assertThat(conflicts.get()).as("the other concurrent trigger was absorbed as a conflict").isEqualTo(1);

        // And the invariant the index protects actually holds: exactly one active row for the connection.
        long activeRows = syncJobRepository
            .findByConnection_IdAndWorkspace_Id(
                connection.getId(),
                workspace.getId(),
                org.springframework.data.domain.Pageable.unpaged()
            )
            .stream()
            .filter(j -> SyncJobStatus.ACTIVE.contains(j.getStatus()))
            .count();
        assertThat(activeRows).isEqualTo(1);
    }
}
