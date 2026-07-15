package de.tum.cit.aet.hephaestus.integration.scm.gitlab.sync.status;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.integration.core.connection.ConnectionConfig;
import de.tum.cit.aet.hephaestus.integration.core.connection.ConnectionService;
import de.tum.cit.aet.hephaestus.integration.core.framework.SyncSchedulerProperties;
import de.tum.cit.aet.hephaestus.integration.core.spi.ConnectionSyncDetails;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationRef;
import de.tum.cit.aet.hephaestus.integration.core.spi.RateLimitSnapshot;
import de.tum.cit.aet.hephaestus.integration.core.spi.SyncResourceState;
import de.tum.cit.aet.hephaestus.integration.scm.domain.issue.IssueRepository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.repository.Repository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.repository.RepositoryRepository;
import de.tum.cit.aet.hephaestus.integration.scm.gitlab.common.GitLabRateLimitTracker;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import de.tum.cit.aet.hephaestus.workspace.RepositoryToMonitor;
import de.tum.cit.aet.hephaestus.workspace.RepositoryToMonitorRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.test.util.ReflectionTestUtils;

@Tag("unit")
class GitlabConnectionSyncStateProviderTest extends BaseUnitTest {

    private static final long WORKSPACE_ID = 7L;
    private static final long CONNECTION_ID = 70L;

    @Mock
    private ConnectionService connectionService;

    @Mock
    private ObjectProvider<GitLabRateLimitTracker> rateLimitTrackerProvider;

    @Mock
    private GitLabRateLimitTracker rateLimitTracker;

    @Mock
    private RepositoryToMonitorRepository repositoryToMonitorRepository;

    @Mock
    private RepositoryRepository repositoryRepository;

    @Mock
    private IssueRepository issueRepository;

    private GitlabConnectionSyncStateProvider provider;
    private IntegrationRef ref;

    @BeforeEach
    void setUp() {
        SyncSchedulerProperties syncProps = new SyncSchedulerProperties(
            true,
            7,
            "0 0 3 * * *",
            15,
            null,
            null,
            null,
            null
        );
        provider = new GitlabConnectionSyncStateProvider(
            connectionService,
            rateLimitTrackerProvider,
            syncProps,
            repositoryToMonitorRepository,
            repositoryRepository,
            issueRepository
        );
        ref = new IntegrationRef(IntegrationKind.GITLAB, WORKSPACE_ID, "gitlab.com:1");
        lenient().when(rateLimitTrackerProvider.getIfAvailable()).thenReturn(rateLimitTracker);
    }

    @Nested
    class Describe {

        @Test
        void shouldReportWebhookRegisteredWhenIdPresent() {
            when(connectionService.findActiveGitLabConfig(WORKSPACE_ID)).thenReturn(Optional.of(gitLabConfig(99L)));

            ConnectionSyncDetails details = provider.describe(ref, CONNECTION_ID);

            assertThat(details.webhookRegistered()).isTrue();
        }

        @Test
        void shouldReportWebhookNotRegisteredWhenIdAbsent() {
            when(connectionService.findActiveGitLabConfig(WORKSPACE_ID)).thenReturn(Optional.of(gitLabConfig(null)));

            ConnectionSyncDetails details = provider.describe(ref, CONNECTION_ID);

            assertThat(details.webhookRegistered()).isFalse();
        }

        @Test
        void shouldReportUnknownWebhookWhenNoActiveConfig() {
            when(connectionService.findActiveGitLabConfig(WORKSPACE_ID)).thenReturn(Optional.empty());

            ConnectionSyncDetails details = provider.describe(ref, CONNECTION_ID);

            assertThat(details.webhookRegistered()).isNull();
        }

        @Test
        void shouldReportRateLimitSnapshotFromTracker() {
            when(connectionService.findActiveGitLabConfig(WORKSPACE_ID)).thenReturn(Optional.empty());
            RateLimitSnapshot snapshot = new RateLimitSnapshot(100, 42, Instant.now().plusSeconds(30));
            when(rateLimitTracker.snapshot(WORKSPACE_ID)).thenReturn(snapshot);

            ConnectionSyncDetails details = provider.describe(ref, CONNECTION_ID);

            assertThat(details.rateLimit()).isEqualTo(snapshot);
        }

        @Test
        void shouldReportNullRateLimitWhenTrackerUnavailable() {
            when(connectionService.findActiveGitLabConfig(WORKSPACE_ID)).thenReturn(Optional.empty());
            when(rateLimitTrackerProvider.getIfAvailable()).thenReturn(null);

            ConnectionSyncDetails details = provider.describe(ref, CONNECTION_ID);

            assertThat(details.rateLimit()).isNull();
        }

        @Test
        void shouldComputeNextScheduledSyncAtFromCron() {
            when(connectionService.findActiveGitLabConfig(WORKSPACE_ID)).thenReturn(Optional.empty());

            ConnectionSyncDetails details = provider.describe(ref, CONNECTION_ID);

            assertThat(details.nextScheduledSyncAt()).isNotNull().isAfter(Instant.now());
        }

        @Test
        void shouldNeverReportBackfillOrDegradedInV1() {
            when(connectionService.findActiveGitLabConfig(WORKSPACE_ID)).thenReturn(Optional.empty());

            ConnectionSyncDetails details = provider.describe(ref, CONNECTION_ID);

            assertThat(details.backfill()).isNull();
            assertThat(details.vendorHealthDegraded()).isFalse();
        }

        private ConnectionConfig.GitLabConfig gitLabConfig(Long webhookId) {
            return new ConnectionConfig.GitLabConfig(
                "https://gitlab.com",
                1L,
                webhookId,
                ConnectionConfig.GitLabConfig.SigningMode.PLAINTEXT,
                Set.of()
            );
        }
    }

    @Nested
    class Resources {

        @Test
        void shouldReturnEmptyWhenNoMonitors() {
            when(repositoryToMonitorRepository.findByWorkspaceId(WORKSPACE_ID)).thenReturn(List.of());

            List<SyncResourceState> resources = provider.resources(ref, CONNECTION_ID);

            assertThat(resources).isEmpty();
        }

        @Test
        void shouldMapSyncedRepositoryWithItemCount() {
            RepositoryToMonitor monitor = monitor(1L, "group/synced-repo");
            when(repositoryToMonitorRepository.findByWorkspaceId(WORKSPACE_ID)).thenReturn(List.of(monitor));

            Repository repo = new Repository();
            ReflectionTestUtils.setField(repo, "id", 500L);
            repo.setNameWithOwner("group/synced-repo");
            Instant syncedAt = Instant.now().minusSeconds(60);
            repo.setLastSyncAt(syncedAt);
            when(repositoryRepository.findAllByWorkspaceMonitors(WORKSPACE_ID)).thenReturn(List.of(repo));

            when(issueRepository.countGroupedByRepositoryIds(List.of(500L))).thenReturn(List.of(itemCount(500L, 12L)));

            List<SyncResourceState> resources = provider.resources(ref, CONNECTION_ID);

            assertThat(resources).hasSize(1);
            SyncResourceState resource = resources.get(0);
            assertThat(resource.id()).isEqualTo(1L);
            assertThat(resource.externalId()).isEqualTo("group/synced-repo");
            assertThat(resource.type()).isEqualTo(SyncResourceState.Type.REPOSITORY);
            assertThat(resource.state()).isEqualTo("SYNCED");
            assertThat(resource.lastSyncedAt()).isEqualTo(syncedAt);
            assertThat(resource.itemCount()).isEqualTo(12L);
            assertThat(resource.lastError()).isNull();
        }

        @Test
        void shouldMapUnsyncedRepositoryAsPendingWithNullItemCount() {
            RepositoryToMonitor monitor = monitor(2L, "group/new-repo");
            when(repositoryToMonitorRepository.findByWorkspaceId(WORKSPACE_ID)).thenReturn(List.of(monitor));
            when(repositoryRepository.findAllByWorkspaceMonitors(WORKSPACE_ID)).thenReturn(List.of());

            List<SyncResourceState> resources = provider.resources(ref, CONNECTION_ID);

            assertThat(resources).hasSize(1);
            SyncResourceState resource = resources.get(0);
            assertThat(resource.state()).isEqualTo("PENDING");
            assertThat(resource.lastSyncedAt()).isNull();
            assertThat(resource.itemCount()).isNull();
        }

        @Test
        void shouldDefaultItemCountToZeroWhenRepositoryHasNoIssuesOrMrs() {
            RepositoryToMonitor monitor = monitor(3L, "group/empty-repo");
            when(repositoryToMonitorRepository.findByWorkspaceId(WORKSPACE_ID)).thenReturn(List.of(monitor));

            Repository repo = new Repository();
            ReflectionTestUtils.setField(repo, "id", 501L);
            repo.setNameWithOwner("group/empty-repo");
            repo.setLastSyncAt(Instant.now());
            when(repositoryRepository.findAllByWorkspaceMonitors(WORKSPACE_ID)).thenReturn(List.of(repo));
            when(issueRepository.countGroupedByRepositoryIds(List.of(501L))).thenReturn(List.of());

            List<SyncResourceState> resources = provider.resources(ref, CONNECTION_ID);

            assertThat(resources.get(0).itemCount()).isEqualTo(0L);
        }

        private RepositoryToMonitor monitor(long id, String nameWithOwner) {
            RepositoryToMonitor monitor = new RepositoryToMonitor();
            ReflectionTestUtils.setField(monitor, "id", id);
            monitor.setNameWithOwner(nameWithOwner);
            return monitor;
        }

        private IssueRepository.RepositoryItemCount itemCount(long repositoryId, long count) {
            return new IssueRepository.RepositoryItemCount() {
                @Override
                public Long getRepositoryId() {
                    return repositoryId;
                }

                @Override
                public Long getItemCount() {
                    return count;
                }
            };
        }
    }
}
