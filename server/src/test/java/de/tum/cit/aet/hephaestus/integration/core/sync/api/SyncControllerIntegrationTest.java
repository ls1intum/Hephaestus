package de.tum.cit.aet.hephaestus.integration.core.sync.api;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.integration.core.connection.Connection;
import de.tum.cit.aet.hephaestus.integration.core.connection.ConnectionConfig;
import de.tum.cit.aet.hephaestus.integration.core.connection.ConnectionRepository;
import de.tum.cit.aet.hephaestus.integration.core.spi.ConnectionSyncDetails;
import de.tum.cit.aet.hephaestus.integration.core.spi.ConnectionSyncStateProvider;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationRef;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationState;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationSyncRunner;
import de.tum.cit.aet.hephaestus.integration.core.spi.SyncResourceState;
import de.tum.cit.aet.hephaestus.integration.core.sync.SyncJobHandle;
import de.tum.cit.aet.hephaestus.integration.core.sync.SyncJobStatus;
import de.tum.cit.aet.hephaestus.integration.core.sync.SyncJobType;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.User;
import de.tum.cit.aet.hephaestus.testconfig.TestAuthUtils;
import de.tum.cit.aet.hephaestus.testconfig.WithAdminUser;
import de.tum.cit.aet.hephaestus.testconfig.WithMentorUser;
import de.tum.cit.aet.hephaestus.workspace.AbstractWorkspaceIntegrationTest;
import de.tum.cit.aet.hephaestus.workspace.AccountType;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import de.tum.cit.aet.hephaestus.workspace.WorkspaceMembership.WorkspaceRole;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * Controller-level coverage for the unified sync surface: status for a connected + a non-ACTIVE
 * connection (must NOT 404), the manual-trigger idempotent-absorb ("Sync now" then a duplicate),
 * cancel, the catalog endpoint, and the class-level {@code @RequireAtLeastWorkspaceAdmin} gate.
 *
 * <p>{@link TestSyncProviders} registers a single stub {@link ConnectionSyncStateProvider} +
 * {@link IntegrationSyncRunner} for {@code GITHUB} — this PR ships the core substrate only; real
 * per-kind wiring is a follow-up (see class doc on {@code ConnectionSyncStateProvider}).
 *
 * <p>The test-profile {@code applicationTaskExecutor} runs {@code @Async}/executor work
 * SYNCHRONOUSLY on the calling thread (see {@code TestAsyncConfiguration}), so a POST that
 * dispatches work through it only returns once the dispatched body finishes. Scenarios that need to
 * observe a job while still RUNNING therefore fire the request on a background thread and use the
 * stub runner's cooperative-cancellation loop (or a start/release latch pair) to control timing.
 */
class SyncControllerIntegrationTest extends AbstractWorkspaceIntegrationTest {

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private ConnectionRepository connectionRepository;

    @Autowired
    private StubRunnerState stubRunnerState;

    private Workspace workspace;
    private Connection connection;

    @BeforeEach
    void setUp() {
        stubRunnerState.reset();
        User owner = persistUser("sync-owner-" + System.nanoTime());
        workspace = createWorkspace(
            "sync-ws-" + System.nanoTime(),
            "Sync Observability Test",
            "sync-org",
            AccountType.ORG,
            owner
        );
        connection = connectionRepository.save(
            new Connection(
                workspace,
                IntegrationKind.GITHUB,
                "100",
                new ConnectionConfig.GitHubAppConfig(100L, "sync-org", null, Set.of())
            )
        );
        connection.setState(IntegrationState.ACTIVE);
        connection = connectionRepository.save(connection);
    }

    @Test
    @WithAdminUser
    void status_connectedActiveConnection_returns200Healthy() {
        ensureAdminMembership(workspace);

        ConnectionSyncStatusDTO status = statusRequest()
            .expectStatus()
            .isOk()
            .expectBody(ConnectionSyncStatusDTO.class)
            .returnResult()
            .getResponseBody();

        assertThat(status).isNotNull();
        assertThat(status.connectionId()).isEqualTo(connection.getId());
        assertThat(status.connectionState()).isEqualTo(IntegrationState.ACTIVE);
        assertThat(status.health()).isEqualTo(ConnectionHealth.HEALTHY);
        assertThat(status.activeJob()).isNull();
    }

    @Test
    @WithAdminUser
    @DisplayName("a SUSPENDED connection still answers 200 with a state-aware DTO, never 404")
    void status_suspendedConnection_returns200NotSuspendedHealth() {
        ensureAdminMembership(workspace);
        connection.setState(IntegrationState.SUSPENDED);
        connectionRepository.save(connection);

        ConnectionSyncStatusDTO status = statusRequest()
            .expectStatus()
            .isOk()
            .expectBody(ConnectionSyncStatusDTO.class)
            .returnResult()
            .getResponseBody();

        assertThat(status).isNotNull();
        assertThat(status.health()).isEqualTo(ConnectionHealth.SUSPENDED);
    }

    @Test
    @WithAdminUser
    void status_unknownConnectionId_returns404() {
        ensureAdminMembership(workspace);

        webTestClient
            .get()
            .uri("/workspaces/{slug}/connections/{id}/sync", workspace.getWorkspaceSlug(), connection.getId() + 999_999)
            .headers(TestAuthUtils.withCurrentUser())
            .exchange()
            .expectStatus()
            .isNotFound();
    }

    @Test
    @WithAdminUser
    void trigger_thenDuplicateWhileRunning_isIdempotentAbsorb() throws InterruptedException {
        ensureAdminMembership(workspace);
        // Captured on the TEST thread: @WithAdminUser's SecurityContext lives in a plain (non-inheritable)
        // ThreadLocal, so a background Thread sees no authentication unless the token travels explicitly.
        String adminToken = TestAuthUtils.getCurrentUserToken();
        CountDownLatch runnerStarted = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        stubRunnerState.reconcile = (ref, handle) -> {
            runnerStarted.countDown();
            try {
                release.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        };

        AtomicReference<WebTestClient.ResponseSpec> firstResponse = new AtomicReference<>();
        Thread firstCaller = new Thread(() -> firstResponse.set(triggerRequest(adminToken)));
        firstCaller.start();

        assertThat(runnerStarted.await(5, TimeUnit.SECONDS)).isTrue();

        // Duplicate manual trigger while the first job is still RUNNING: idempotent-absorb, 200 not 409.
        SyncJobDTO duplicateJob = triggerRequest(adminToken)
            .expectStatus()
            .isOk()
            .expectBody(SyncJobDTO.class)
            .returnResult()
            .getResponseBody();
        assertThat(duplicateJob).isNotNull();

        release.countDown();
        firstCaller.join(5000);

        SyncJobDTO firstJob = firstResponse
            .get()
            .expectStatus()
            .isEqualTo(HttpStatus.ACCEPTED)
            .expectBody(SyncJobDTO.class)
            .returnResult()
            .getResponseBody();
        assertThat(firstJob).isNotNull();
        assertThat(duplicateJob.id()).isEqualTo(firstJob.id());
    }

    @Test
    @WithAdminUser
    void trigger_notActiveConnection_returns409() {
        ensureAdminMembership(workspace);
        connection.setState(IntegrationState.SUSPENDED);
        connectionRepository.save(connection);

        triggerRequest(TestAuthUtils.getCurrentUserToken()).expectStatus().isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    @WithAdminUser
    void cancel_runningJob_stopsItCooperatively() throws InterruptedException {
        ensureAdminMembership(workspace);
        String adminToken = TestAuthUtils.getCurrentUserToken();
        CountDownLatch runnerStarted = new CountDownLatch(1);
        stubRunnerState.reconcile = (ref, handle) -> {
            runnerStarted.countDown();
            long deadline = System.currentTimeMillis() + 5000;
            while (!handle.isCancellationRequested() && System.currentTimeMillis() < deadline) {
                try {
                    Thread.sleep(25);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        };

        AtomicReference<WebTestClient.ResponseSpec> firstResponse = new AtomicReference<>();
        Thread firstCaller = new Thread(() -> firstResponse.set(triggerRequest(adminToken)));
        firstCaller.start();
        assertThat(runnerStarted.await(5, TimeUnit.SECONDS)).isTrue();

        SyncJobDTO created = duplicateJobDuringRun(adminToken);

        webTestClient
            .post()
            .uri(
                "/workspaces/{slug}/connections/{id}/sync/jobs/{jobId}/cancel",
                workspace.getWorkspaceSlug(),
                connection.getId(),
                created.id()
            )
            .headers(h -> h.setBearerAuth(adminToken))
            .exchange()
            .expectStatus()
            .isEqualTo(HttpStatus.ACCEPTED);

        firstCaller.join(5000);

        ConnectionSyncStatusDTO status = statusRequest()
            .expectStatus()
            .isOk()
            .expectBody(ConnectionSyncStatusDTO.class)
            .returnResult()
            .getResponseBody();
        assertThat(status).isNotNull();
        assertThat(status.activeJob()).isNull();
        assertThat(status.lastJob()).isNotNull();
        assertThat(status.lastJob().status()).isEqualTo(SyncJobStatus.CANCELLED);
    }

    @Test
    @WithAdminUser
    void catalog_listsGithubAsConnected() {
        ensureAdminMembership(workspace);

        List<IntegrationCatalogEntryDTO> catalog = webTestClient
            .get()
            .uri("/workspaces/{slug}/connections/catalog", workspace.getWorkspaceSlug())
            .headers(TestAuthUtils.withCurrentUser())
            .exchange()
            .expectStatus()
            .isOk()
            .expectBodyList(IntegrationCatalogEntryDTO.class)
            .returnResult()
            .getResponseBody();

        assertThat(catalog).isNotNull();
        IntegrationCatalogEntryDTO github = catalog
            .stream()
            .filter(e -> e.kind() == IntegrationKind.GITHUB)
            .findFirst()
            .orElseThrow(() -> new AssertionError("GITHUB missing from catalog"));
        assertThat(github.connected()).isTrue();
        assertThat(github.connectionId()).isEqualTo(connection.getId());
    }

    @Test
    @WithMentorUser
    @DisplayName("a workspace MEMBER is forbidden from every sync endpoint")
    void nonAdminMember_forbiddenEverywhere() {
        User member = persistUser("mentor");
        ensureWorkspaceMembership(workspace, member, WorkspaceRole.MEMBER);

        statusRequest().expectStatus().isForbidden();
        webTestClient
            .get()
            .uri("/workspaces/{slug}/connections/catalog", workspace.getWorkspaceSlug())
            .headers(TestAuthUtils.withCurrentUser())
            .exchange()
            .expectStatus()
            .isForbidden();
        triggerRequest(TestAuthUtils.getCurrentUserToken()).expectStatus().isForbidden();
    }

    // --- helpers ---

    private WebTestClient.ResponseSpec statusRequest() {
        return webTestClient
            .get()
            .uri("/workspaces/{slug}/connections/{id}/sync", workspace.getWorkspaceSlug(), connection.getId())
            .headers(TestAuthUtils.withCurrentUser())
            .exchange();
    }

    /**
     * Takes an explicit bearer token (rather than reading {@code TestAuthUtils.withCurrentUser()} at
     * call time) so it can be invoked from a background {@link Thread}: {@code SecurityContextHolder}'s
     * default strategy is a plain (non-inheritable) {@code ThreadLocal}, so a child thread sees no
     * authentication unless the token is captured on the test thread and passed in explicitly.
     */
    private WebTestClient.ResponseSpec triggerRequest(String bearerToken) {
        return webTestClient
            .post()
            .uri("/workspaces/{slug}/connections/{id}/sync/jobs", workspace.getWorkspaceSlug(), connection.getId())
            .headers(h -> h.setBearerAuth(bearerToken))
            .bodyValue(new TriggerSyncJobRequestDTO(SyncJobType.RECONCILIATION))
            .exchange();
    }

    /** A trigger call made WHILE another job is running answers 200 with the still-active job. */
    private SyncJobDTO duplicateJobDuringRun(String bearerToken) {
        return triggerRequest(bearerToken)
            .expectStatus()
            .isOk()
            .expectBody(SyncJobDTO.class)
            .returnResult()
            .getResponseBody();
    }

    /** Mutable per-test hook the stub runner delegates to; reset between tests. */
    static class StubRunnerState {

        volatile BiConsumer<IntegrationRef, SyncJobHandle> reconcile = (ref, handle) -> {};

        void reset() {
            reconcile = (ref, handle) -> {};
        }
    }

    @TestConfiguration
    static class TestSyncProviders {

        @Bean
        StubRunnerState stubRunnerState() {
            return new StubRunnerState();
        }

        @Bean
        ConnectionSyncStateProvider githubSyncStateProvider() {
            return new ConnectionSyncStateProvider() {
                @Override
                public IntegrationKind kind() {
                    return IntegrationKind.GITHUB;
                }

                @Override
                public ConnectionSyncDetails describe(IntegrationRef ref, long connectionId) {
                    return ConnectionSyncDetails.empty();
                }

                @Override
                public List<SyncResourceState> resources(IntegrationRef ref, long connectionId) {
                    return List.of();
                }
            };
        }

        @Bean
        IntegrationSyncRunner githubSyncRunner(StubRunnerState state) {
            return new IntegrationSyncRunner() {
                @Override
                public IntegrationKind kind() {
                    return IntegrationKind.GITHUB;
                }

                @Override
                public void reconcile(IntegrationRef ref, SyncJobHandle handle) {
                    state.reconcile.accept(ref, handle);
                }
            };
        }
    }
}
