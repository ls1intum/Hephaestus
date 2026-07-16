package de.tum.cit.aet.hephaestus.integration.scm.github.sync.status;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.integration.core.connection.Connection;
import de.tum.cit.aet.hephaestus.integration.core.connection.ConnectionConfig;
import de.tum.cit.aet.hephaestus.integration.core.connection.ConnectionRepository;
import de.tum.cit.aet.hephaestus.integration.core.framework.SyncSchedulerProperties;
import de.tum.cit.aet.hephaestus.integration.core.framework.SyncSchedulerProperties.BackfillProperties;
import de.tum.cit.aet.hephaestus.integration.core.framework.SyncSchedulerProperties.FilterProperties;
import de.tum.cit.aet.hephaestus.integration.core.spi.BackfillSummary;
import de.tum.cit.aet.hephaestus.integration.core.spi.ConnectionSyncDetails;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationRef;
import de.tum.cit.aet.hephaestus.integration.core.spi.RateLimitSnapshot;
import de.tum.cit.aet.hephaestus.integration.core.spi.SyncResourceState;
import de.tum.cit.aet.hephaestus.integration.scm.domain.issue.IssueRepository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.repository.Repository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.repository.RepositoryRepository;
import de.tum.cit.aet.hephaestus.integration.scm.github.common.ScopedRateLimitTracker;
import de.tum.cit.aet.hephaestus.integration.scm.github.workspace.GitHubInstallationSuspensionTracker;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import de.tum.cit.aet.hephaestus.testconfig.WorkspaceTestFixtures;
import de.tum.cit.aet.hephaestus.workspace.RepositoryToMonitor;
import de.tum.cit.aet.hephaestus.workspace.RepositoryToMonitorRepository;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.test.util.ReflectionTestUtils;

class GithubConnectionSyncStateProviderTest extends BaseUnitTest {

    private static final long WORKSPACE_ID = 1L;
    private static final long CONNECTION_ID = 10L;
    private static final long INSTALLATION_ID = 100L;

    @Mock
    private ConnectionRepository connectionRepository;

    @Mock
    private ScopedRateLimitTracker rateLimitTracker;

    @Mock
    private GitHubInstallationSuspensionTracker suspensionTracker;

    @Mock
    private RepositoryToMonitorRepository repositoryToMonitorRepository;

    @Mock
    private RepositoryRepository repositoryRepository;

    @Mock
    private IssueRepository issueRepository;

    private Workspace workspace;
    private IntegrationRef ref;

    @BeforeEach
    void setUp() {
        workspace = new Workspace();
        workspace.setId(WORKSPACE_ID);
        ref = new IntegrationRef(IntegrationKind.GITHUB, WORKSPACE_ID, String.valueOf(INSTALLATION_ID));
    }

    private GithubConnectionSyncStateProvider provider(SyncSchedulerProperties properties) {
        return new GithubConnectionSyncStateProvider(
            connectionRepository,
            rateLimitTracker,
            suspensionTracker,
            properties,
            repositoryToMonitorRepository,
            repositoryRepository,
            issueRepository
        );
    }

    private static SyncSchedulerProperties schedulerProperties(boolean backfillEnabled) {
        return new SyncSchedulerProperties(
            true,
            7,
            "0 0 3 * * *",
            15,
            new BackfillProperties(backfillEnabled, 50, 100, 60),
            new FilterProperties(Set.of(), Set.of(), Set.of()),
            null,
            null
        );
    }

    private Connection githubAppConnection() {
        Connection connection = new Connection(
            workspace,
            IntegrationKind.GITHUB,
            String.valueOf(INSTALLATION_ID),
            new ConnectionConfig.GitHubAppConfig(INSTALLATION_ID, "acme", null, Set.of())
        );
        ReflectionTestUtils.setField(connection, "id", CONNECTION_ID);
        return connection;
    }

    private Connection githubPatConnection() {
        Connection connection = new Connection(
            workspace,
            IntegrationKind.GITHUB,
            "pat",
            new ConnectionConfig.GitHubPatConfig("acme", null, Set.of())
        );
        ReflectionTestUtils.setField(connection, "id", CONNECTION_ID);
        return connection;
    }

    @Nested
    class Describe {

        @Test
        void appInstallationNotSuspended_webhookRegisteredTrueAndHealthy() {
            when(connectionRepository.findById(CONNECTION_ID)).thenReturn(Optional.of(githubAppConnection()));
            when(suspensionTracker.isInstallationMarkedSuspended(INSTALLATION_ID)).thenReturn(false);

            ConnectionSyncDetails details = provider(schedulerProperties(false)).describe(ref, CONNECTION_ID);

            assertThat(details.webhookRegistered()).isTrue();
            assertThat(details.vendorHealthDegraded()).isFalse();
        }

        @Test
        void appInstallationSuspended_vendorHealthDegraded() {
            when(connectionRepository.findById(CONNECTION_ID)).thenReturn(Optional.of(githubAppConnection()));
            when(suspensionTracker.isInstallationMarkedSuspended(INSTALLATION_ID)).thenReturn(true);

            ConnectionSyncDetails details = provider(schedulerProperties(false)).describe(ref, CONNECTION_ID);

            assertThat(details.vendorHealthDegraded()).isTrue();
        }

        @Test
        void patConnection_webhookRegisteredNull() {
            when(connectionRepository.findById(CONNECTION_ID)).thenReturn(Optional.of(githubPatConnection()));

            ConnectionSyncDetails details = provider(schedulerProperties(false)).describe(ref, CONNECTION_ID);

            assertThat(details.webhookRegistered()).isNull();
            assertThat(details.vendorHealthDegraded()).isFalse();
        }

        @Test
        void rateLimitSnapshot_passedThroughFromTracker() {
            when(connectionRepository.findById(CONNECTION_ID)).thenReturn(Optional.of(githubAppConnection()));
            RateLimitSnapshot snapshot = new RateLimitSnapshot(5000, 4200, Instant.now().plusSeconds(1800));
            when(rateLimitTracker.snapshot(WORKSPACE_ID)).thenReturn(snapshot);

            ConnectionSyncDetails details = provider(schedulerProperties(false)).describe(ref, CONNECTION_ID);

            assertThat(details.rateLimit()).isEqualTo(snapshot);
        }

        @Test
        void rateLimitNeverObserved_isNull() {
            when(connectionRepository.findById(CONNECTION_ID)).thenReturn(Optional.of(githubAppConnection()));

            ConnectionSyncDetails details = provider(schedulerProperties(false)).describe(ref, CONNECTION_ID);

            assertThat(details.rateLimit()).isNull();
        }

        @Test
        void nextScheduledSyncAt_isTheCronsNextOccurrence() {
            when(connectionRepository.findById(CONNECTION_ID)).thenReturn(Optional.of(githubAppConnection()));
            Instant before = Instant.now();

            ConnectionSyncDetails details = provider(schedulerProperties(false)).describe(ref, CONNECTION_ID);

            // The configured cron fires daily at 03:00: assert the actual next occurrence, not merely
            // "in the future" (which any future instant, e.g. now+1s, would satisfy).
            Instant next = details.nextScheduledSyncAt();
            assertThat(next).isNotNull();
            ZonedDateTime fire = next.atZone(ZoneId.systemDefault());
            assertThat(fire.getHour()).isEqualTo(3);
            assertThat(fire.getMinute()).isZero();
            assertThat(fire.getSecond()).isZero();
            assertThat(next).isAfter(before).isBeforeOrEqualTo(before.plus(Duration.ofDays(1)));
        }

        @Test
        void nextScheduledSyncAt_invalidCron_isNull() {
            when(connectionRepository.findById(CONNECTION_ID)).thenReturn(Optional.of(githubAppConnection()));
            SyncSchedulerProperties broken = new SyncSchedulerProperties(
                true,
                7,
                "not a cron",
                15,
                new BackfillProperties(false, 50, 100, 60),
                new FilterProperties(Set.of(), Set.of(), Set.of()),
                null,
                null
            );

            assertThat(provider(broken).describe(ref, CONNECTION_ID).nextScheduledSyncAt()).isNull();
        }

        @Test
        void backfillDisabled_stateIsDisabled() {
            when(connectionRepository.findById(CONNECTION_ID)).thenReturn(Optional.of(githubAppConnection()));

            ConnectionSyncDetails details = provider(schedulerProperties(false)).describe(ref, CONNECTION_ID);

            assertThat(details.backfill()).isEqualTo(new BackfillSummary("DISABLED", null, null));
        }

        @Test
        void backfillEnabledNoMonitoredRepos_stateIsNotStarted() {
            when(connectionRepository.findById(CONNECTION_ID)).thenReturn(Optional.of(githubAppConnection()));
            when(repositoryToMonitorRepository.findByWorkspaceId(WORKSPACE_ID)).thenReturn(List.of());

            ConnectionSyncDetails details = provider(schedulerProperties(true)).describe(ref, CONNECTION_ID);

            assertThat(details.backfill().state()).isEqualTo("NOT_STARTED");
            assertThat(details.backfill().percent()).isNull();
        }

        @Test
        void backfillEnabledAllComplete_stateIsCompleteAndPercentIsFull() {
            when(connectionRepository.findById(CONNECTION_ID)).thenReturn(Optional.of(githubAppConnection()));
            RepositoryToMonitor rtm = WorkspaceTestFixtures.repositoryMonitor(workspace, "acme/repo-a");
            rtm.setId(500L);
            rtm.setIssueBackfillHighWaterMark(100);
            rtm.setIssueBackfillCheckpoint(0);
            rtm.setPullRequestBackfillHighWaterMark(0);
            rtm.setPullRequestBackfillCheckpoint(0);
            when(repositoryToMonitorRepository.findByWorkspaceId(WORKSPACE_ID)).thenReturn(List.of(rtm));

            ConnectionSyncDetails details = provider(schedulerProperties(true)).describe(ref, CONNECTION_ID);

            assertThat(details.backfill().state()).isEqualTo("COMPLETE");
            assertThat(details.backfill().percent()).isEqualTo(100);
        }

        @Test
        void backfillEnabledPartialProgress_stateIsInProgressWithCoarsePercent() {
            when(connectionRepository.findById(CONNECTION_ID)).thenReturn(Optional.of(githubAppConnection()));
            RepositoryToMonitor rtm = WorkspaceTestFixtures.repositoryMonitor(workspace, "acme/repo-a");
            rtm.setId(500L);
            rtm.setIssueBackfillHighWaterMark(100);
            rtm.setIssueBackfillCheckpoint(25);
            rtm.setPullRequestBackfillHighWaterMark(0);
            rtm.setPullRequestBackfillCheckpoint(0);
            when(repositoryToMonitorRepository.findByWorkspaceId(WORKSPACE_ID)).thenReturn(List.of(rtm));

            ConnectionSyncDetails details = provider(schedulerProperties(true)).describe(ref, CONNECTION_ID);

            assertThat(details.backfill().state()).isEqualTo("IN_PROGRESS");
            assertThat(details.backfill().percent()).isEqualTo(75);
        }
    }

    @Nested
    class Resources {

        @Test
        void noMonitoredRepositories_returnsEmptyList() {
            when(repositoryToMonitorRepository.findByWorkspaceId(WORKSPACE_ID)).thenReturn(List.of());

            List<SyncResourceState> resources = provider(schedulerProperties(false)).resources(ref, CONNECTION_ID);

            assertThat(resources).isEmpty();
        }

        @Test
        void mapsRepositoryToMonitor_withItemCountFromGroupedQuery() {
            RepositoryToMonitor rtm = WorkspaceTestFixtures.repositoryMonitor(workspace, "acme/repo-a");
            rtm.setId(500L);
            Instant issuesSyncedAt = Instant.now().minusSeconds(60);
            Instant prsSyncedAt = Instant.now().minusSeconds(30);
            rtm.setIssuesSyncedAt(issuesSyncedAt);
            rtm.setPullRequestsSyncedAt(prsSyncedAt);
            when(repositoryToMonitorRepository.findByWorkspaceId(WORKSPACE_ID)).thenReturn(List.of(rtm));

            Repository repository = new Repository();
            repository.setId(900L);
            repository.setNameWithOwner("acme/repo-a");
            when(repositoryRepository.findAllByWorkspaceMonitors(WORKSPACE_ID)).thenReturn(List.of(repository));

            IssueRepository.RepositoryItemCount countRow = new IssueRepository.RepositoryItemCount() {
                @Override
                public Long getRepositoryId() {
                    return 900L;
                }

                @Override
                public Long getItemCount() {
                    return 42L;
                }
            };
            when(issueRepository.countGroupedByRepositoryIds(anyCollection())).thenReturn(List.of(countRow));

            List<SyncResourceState> resources = provider(schedulerProperties(false)).resources(ref, CONNECTION_ID);

            assertThat(resources).hasSize(1);
            SyncResourceState resource = resources.get(0);
            assertThat(resource.id()).isEqualTo(500L);
            assertThat(resource.externalId()).isEqualTo("acme/repo-a");
            assertThat(resource.type()).isEqualTo(SyncResourceState.Type.REPOSITORY);
            assertThat(resource.state()).isEqualTo("SYNCED");
            assertThat(resource.lastSyncedAt()).isEqualTo(prsSyncedAt);
            assertThat(resource.itemCount()).isEqualTo(42L);
            assertThat(resource.upstreamCount()).isNull();
            assertThat(resource.lastError()).isNull();
        }

        @Test
        void repositoryPendingInitialSync_stateReflectsPending() {
            RepositoryToMonitor rtm = WorkspaceTestFixtures.repositoryMonitor(workspace, "acme/repo-b");
            rtm.setId(501L);
            when(repositoryToMonitorRepository.findByWorkspaceId(WORKSPACE_ID)).thenReturn(List.of(rtm));
            when(repositoryRepository.findAllByWorkspaceMonitors(WORKSPACE_ID)).thenReturn(List.of());

            List<SyncResourceState> resources = provider(schedulerProperties(false)).resources(ref, CONNECTION_ID);

            assertThat(resources).hasSize(1);
            assertThat(resources.get(0).state()).isEqualTo("PENDING");
            assertThat(resources.get(0).itemCount()).isNull();
            assertThat(resources.get(0).lastSyncedAt()).isNull();
        }

        @Test
        void backfillInProgress_percentReflectsRemainingAgainstHighWaterMark() {
            RepositoryToMonitor rtm = WorkspaceTestFixtures.repositoryMonitor(workspace, "acme/repo-c");
            rtm.setId(502L);
            rtm.setIssuesSyncedAt(Instant.now());
            rtm.setPullRequestsSyncedAt(Instant.now());
            rtm.setIssueBackfillHighWaterMark(100);
            rtm.setIssueBackfillCheckpoint(25);
            rtm.setPullRequestBackfillHighWaterMark(0);
            rtm.setPullRequestBackfillCheckpoint(0);
            when(repositoryToMonitorRepository.findByWorkspaceId(WORKSPACE_ID)).thenReturn(List.of(rtm));
            when(repositoryRepository.findAllByWorkspaceMonitors(WORKSPACE_ID)).thenReturn(List.of());

            List<SyncResourceState> resources = provider(schedulerProperties(false)).resources(ref, CONNECTION_ID);

            assertThat(resources).hasSize(1);
            assertThat(resources.get(0).backfillPercent()).isEqualTo(75);
            assertThat(resources.get(0).backfillCompletedThrough()).isNull();
        }
    }
}
