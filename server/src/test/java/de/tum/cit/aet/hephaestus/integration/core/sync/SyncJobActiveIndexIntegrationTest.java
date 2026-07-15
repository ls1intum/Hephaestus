package de.tum.cit.aet.hephaestus.integration.core.sync;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.tum.cit.aet.hephaestus.integration.core.connection.Connection;
import de.tum.cit.aet.hephaestus.integration.core.connection.ConnectionConfig;
import de.tum.cit.aet.hephaestus.integration.core.connection.ConnectionRepository;
import de.tum.cit.aet.hephaestus.integration.core.connection.ConnectionService;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationState;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.User;
import de.tum.cit.aet.hephaestus.workspace.AbstractWorkspaceIntegrationTest;
import de.tum.cit.aet.hephaestus.workspace.AccountType;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import java.util.List;
import java.util.Map;
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
import org.springframework.transaction.annotation.Transactional;

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
    private ConnectionService connectionService;

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

    @Test
    void uninstallRefusesToCrossLifecycleBoundaryWhileSyncJobIsActive() {
        SyncJob active = syncJobRepository.saveAndFlush(
            new SyncJob(
                workspace,
                connection,
                IntegrationKind.GITHUB,
                SyncJobType.RECONCILIATION,
                SyncJobTrigger.MANUAL,
                null
            )
        );

        assertThatThrownBy(() ->
            connectionService.transition(
                connection,
                new ConnectionService.TransitionRequest(
                    IntegrationState.UNINSTALLED,
                    "DISCONNECT",
                    "ADMIN",
                    "test-admin",
                    "disconnect-race-test",
                    "disconnect"
                )
            )
        ).hasMessageContaining("active sync job");

        assertThat(connectionRepository.findById(connection.getId()).orElseThrow().getState()).isEqualTo(
            IntegrationState.ACTIVE
        );
        assertThat(syncJobRepository.findById(active.getId()).orElseThrow().getStatus()).isEqualTo(
            SyncJobStatus.PENDING
        );
    }

    @Test
    @Transactional
    void terminalTransition_isCompareAndSet_andPersistsProgressJson() {
        SyncJob job = syncJobRepository.saveAndFlush(
            new SyncJob(
                workspace,
                connection,
                IntegrationKind.GITHUB,
                SyncJobType.RECONCILIATION,
                SyncJobTrigger.MANUAL,
                null
            )
        );
        assertThat(syncJobRepository.markRunning(job.getId())).isEqualTo(1);

        int completed = syncJobRepository.completeActiveJob(
            job.getId(),
            SyncJobStatus.SUCCEEDED_WITH_WARNINGS,
            null,
            3,
            5,
            Map.of("warnings", 1),
            SyncJobStatus.ACTIVE,
            true
        );

        assertThat(completed).isEqualTo(1);
        SyncJob persisted = syncJobRepository.findById(job.getId()).orElseThrow();
        assertThat(persisted.getStatus()).isEqualTo(SyncJobStatus.SUCCEEDED_WITH_WARNINGS);
        assertThat(persisted.getProgress()).containsEntry("warnings", 1);
        assertThat(
            syncJobRepository.completeActiveJob(
                job.getId(),
                SyncJobStatus.FAILED,
                "late writer",
                0,
                5,
                Map.of(),
                SyncJobStatus.ACTIVE,
                true
            )
        ).isZero();
        assertThat(syncJobRepository.findById(job.getId()).orElseThrow().getStatus()).isEqualTo(
            SyncJobStatus.SUCCEEDED_WITH_WARNINGS
        );
    }

    @Test
    @Transactional
    void reaperUpdate_rechecksLeaseAfterSelectingCandidate() {
        SyncJob job = syncJobRepository.saveAndFlush(
            new SyncJob(
                workspace,
                connection,
                IntegrationKind.GITHUB,
                SyncJobType.RECONCILIATION,
                SyncJobTrigger.MANUAL,
                null
            )
        );
        assertThat(syncJobRepository.markRunning(job.getId())).isEqualTo(1);
        jdbcTemplate.update("UPDATE sync_job SET heartbeat_at = now() - interval '1 hour' WHERE id = ?", job.getId());
        assertThat(syncJobRepository.findAbandoned(900)).extracting(SyncJob::getId).contains(job.getId());

        jdbcTemplate.update("UPDATE sync_job SET heartbeat_at = now() WHERE id = ?", job.getId());

        assertThat(syncJobRepository.markAbandoned(job.getId(), "stale", 900)).isZero();
        assertThat(syncJobRepository.findById(job.getId()).orElseThrow().getStatus()).isEqualTo(SyncJobStatus.RUNNING);
    }
}
