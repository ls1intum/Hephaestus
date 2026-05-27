package de.tum.cit.aet.hephaestus.leaderboard.tasks;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.config.ApplicationProperties;
import de.tum.cit.aet.hephaestus.integration.core.connection.Connection;
import de.tum.cit.aet.hephaestus.integration.core.connection.ConnectionConfig;
import de.tum.cit.aet.hephaestus.integration.core.connection.ConnectionRepository;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationState;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.UserInfoDTO;
import de.tum.cit.aet.hephaestus.integration.slack.messaging.SlackMessageService;
import de.tum.cit.aet.hephaestus.integration.slack.messaging.SlackSendException;
import de.tum.cit.aet.hephaestus.leaderboard.LeaderboardEntryDTO;
import de.tum.cit.aet.hephaestus.leaderboard.LeaderboardMode;
import de.tum.cit.aet.hephaestus.leaderboard.LeaderboardProperties;
import de.tum.cit.aet.hephaestus.leaderboard.LeaderboardService;
import de.tum.cit.aet.hephaestus.leaderboard.LeaderboardSortType;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;

class SlackWeeklyLeaderboardTaskTest extends BaseUnitTest {

    @Mock
    private LeaderboardService leaderboardService;

    @Mock
    private SlackMessageService slackMessageService;

    @Mock
    private ConnectionRepository connectionRepository;

    private SlackWeeklyLeaderboardTask task;

    @BeforeEach
    void setUp() {
        ApplicationProperties appProps = new ApplicationProperties(
            "https://app.test",
            new ApplicationProperties.Webapp("https://app.test")
        );
        LeaderboardProperties leaderboardProps = new LeaderboardProperties(
            new LeaderboardProperties.Schedule(2, "09:00"),
            new LeaderboardProperties.Notification(true)
        );
        task = new SlackWeeklyLeaderboardTask(
            leaderboardProps,
            appProps,
            slackMessageService,
            leaderboardService,
            connectionRepository
        );
    }

    @Test
    void run_noActiveSlackConnections_skipsWithoutCallingSlack() {
        when(
            connectionRepository.findByKindAndStateWithWorkspace(IntegrationKind.SLACK, IntegrationState.ACTIVE)
        ).thenReturn(List.of());

        task.run();

        verify(slackMessageService, never()).sendForWorkspace(anyLong(), anyString(), any(), any());
    }

    /**
     * Per-workspace skip preconditions (notifications-off, no channel configured) all
     * route through the same fast-return path. Fold into one parameterised case so the
     * contract stays clear: skip → never call Slack.
     */
    @ParameterizedTest(name = "{0}")
    @MethodSource("skipScenarios")
    void run_perWorkspaceSkipConditions_neverCallSlack(String name, Workspace w, ConnectionConfig.SlackConfig cfg) {
        Connection c = connection(w, cfg);
        when(
            connectionRepository.findByKindAndStateWithWorkspace(IntegrationKind.SLACK, IntegrationState.ACTIVE)
        ).thenReturn(List.of(c));

        task.run();

        verify(slackMessageService, never()).sendForWorkspace(anyLong(), anyString(), any(), any());
    }

    private static Stream<Arguments> skipScenarios() {
        return Stream.of(
            Arguments.of(
                "notificationsDisabled",
                workspace(1L, "acme", false),
                slackConfig("T1", "Acme", "C0974LJBPBK", "engineering")
            ),
            Arguments.of("noChannelConfigured", workspace(1L, "acme", true), slackConfig("T1", "Acme", null, null))
        );
    }

    @Test
    void run_oneWorkspaceFailureDoesNotBlockTheNext() {
        Workspace failing = workspace(1L, "fails", true);
        Workspace ok = workspace(2L, "succeeds", true);
        Connection failingConn = connection(failing, slackConfig("T1", "Fails", "C0974LJBPBK", null));
        Connection okConn = connection(ok, slackConfig("T2", "OK", "C0974LJBPBKZ", null));
        when(
            connectionRepository.findByKindAndStateWithWorkspace(IntegrationKind.SLACK, IntegrationState.ACTIVE)
        ).thenReturn(List.of(failingConn, okConn));

        // Both workspaces produce one top-reviewer; the first send throws.
        when(slackMessageService.listMembers(anyLong())).thenReturn(List.of(slackUser("U1", "alice")));
        when(
            leaderboardService.createLeaderboard(
                any(Workspace.class),
                any(),
                any(),
                anyString(),
                eq(LeaderboardSortType.SCORE),
                eq(LeaderboardMode.INDIVIDUAL)
            )
        ).thenReturn(List.of(leaderboardEntry("alice")));

        // First call throws SlackSendException; second call must still be invoked.
        org.mockito.Mockito.doThrow(new SlackSendException(1L, "C0974LJBPBK", "channel_not_found"))
            .when(slackMessageService)
            .sendForWorkspace(eq(1L), eq("C0974LJBPBK"), any(), any());

        task.run();

        // Both invocations happened: the first threw (caught), the second proceeded.
        verify(slackMessageService, times(1)).sendForWorkspace(eq(1L), eq("C0974LJBPBK"), any(), any());
        verify(slackMessageService, times(1)).sendForWorkspace(eq(2L), eq("C0974LJBPBKZ"), any(), any());
    }

    @Test
    void run_happyPath_postsOncePerQualifyingWorkspace() {
        Workspace w = workspace(99L, "acme", true);
        Connection c = connection(w, slackConfig("T1", "Acme", "C0974LJBPBK", "engineering"));
        when(
            connectionRepository.findByKindAndStateWithWorkspace(IntegrationKind.SLACK, IntegrationState.ACTIVE)
        ).thenReturn(List.of(c));
        when(slackMessageService.listMembers(99L)).thenReturn(List.of(slackUser("U1", "alice")));
        when(
            leaderboardService.createLeaderboard(
                any(Workspace.class),
                any(),
                any(),
                eq("engineering"),
                eq(LeaderboardSortType.SCORE),
                eq(LeaderboardMode.INDIVIDUAL)
            )
        ).thenReturn(List.of(leaderboardEntry("alice")));

        task.run();

        verify(slackMessageService, times(1)).sendForWorkspace(
            eq(99L),
            eq("C0974LJBPBK"),
            any(),
            eq("Weekly review highlights")
        );
    }

    // ─── Test helpers ─────────────────────────────────────────────────────────

    private static Workspace workspace(long id, String slug, boolean notificationsEnabled) {
        Workspace w = new Workspace();
        w.setId(id);
        w.setWorkspaceSlug(slug);
        w.setLeaderboardNotificationEnabled(notificationsEnabled);
        return w;
    }

    private static Connection connection(Workspace w, ConnectionConfig config) {
        return new Connection(w, IntegrationKind.SLACK, "T-" + w.getId(), config);
    }

    private static ConnectionConfig.SlackConfig slackConfig(
        String teamId,
        String teamName,
        String channelId,
        String teamLabel
    ) {
        return new ConnectionConfig.SlackConfig(teamId, teamName, channelId, teamLabel, Set.of());
    }

    private static com.slack.api.model.User slackUser(String id, String name) {
        com.slack.api.model.User u = new com.slack.api.model.User();
        u.setId(id);
        u.setName(name);
        return u;
    }

    private static LeaderboardEntryDTO leaderboardEntry(String name) {
        UserInfoDTO user = new UserInfoDTO(
            1L,
            name,
            name + "@example.com",
            "https://example.com/a.png",
            name,
            "https://example.com/" + name,
            0
        );
        return new LeaderboardEntryDTO(
            /* rank */ 1,
            /* score */ 10,
            /* user */ user,
            /* team */ null,
            /* reviewedPullRequests */ List.of(),
            /* numberOfReviewedPRs */ 0,
            /* numberOfApprovals */ 0,
            /* numberOfChangeRequests */ 0,
            /* numberOfComments */ 0,
            /* numberOfUnknowns */ 0,
            /* numberOfCodeComments */ 0,
            /* numberOfOwnReplies */ 0,
            /* numberOfOpenPullRequests */ 0,
            /* numberOfMergedPullRequests */ 0,
            /* numberOfClosedPullRequests */ 0,
            /* numberOfOpenedIssues */ 0,
            /* numberOfClosedIssues */ 0
        );
    }
}
