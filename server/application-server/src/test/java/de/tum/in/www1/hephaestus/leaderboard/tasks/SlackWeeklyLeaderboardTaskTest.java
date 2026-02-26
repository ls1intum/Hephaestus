package de.tum.in.www1.hephaestus.leaderboard.tasks;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.slack.api.model.User;
import com.slack.api.model.User.Profile;
import com.slack.api.model.block.LayoutBlock;
import de.tum.in.www1.hephaestus.config.ApplicationProperties;
import de.tum.in.www1.hephaestus.gitprovider.user.UserInfoDTO;
import de.tum.in.www1.hephaestus.leaderboard.*;
import de.tum.in.www1.hephaestus.workspace.Workspace;
import de.tum.in.www1.hephaestus.workspace.WorkspaceRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for {@link SlackWeeklyLeaderboardTask}.
 *
 * <p>Verifies per-workspace notification routing, global config fallback,
 * and correct filtering of workspaces based on notification settings.
 */
@Tag("unit")
@DisplayName("SlackWeeklyLeaderboardTask")
@ExtendWith(MockitoExtension.class)
class SlackWeeklyLeaderboardTaskTest {

    private static final String GLOBAL_CHANNEL_ID = "C_GLOBAL";
    private static final String GLOBAL_TEAM = "global-team";

    @Mock
    private SlackMessageService slackMessageService;

    @Mock
    private LeaderboardService leaderboardService;

    @Mock
    private WorkspaceRepository workspaceRepository;

    private SlackWeeklyLeaderboardTask task;

    @BeforeEach
    void setUp() {
        LeaderboardProperties properties = new LeaderboardProperties(
            new LeaderboardProperties.Schedule(1, "09:00"),
            new LeaderboardProperties.Notification(true, GLOBAL_TEAM, GLOBAL_CHANNEL_ID)
        );
        ApplicationProperties appProperties = new ApplicationProperties("https://hephaestus.example.com", null);

        task = new SlackWeeklyLeaderboardTask(
            properties,
            appProperties,
            slackMessageService,
            leaderboardService,
            workspaceRepository
        );
    }

    // ========================================================================
    // Helper Methods
    // ========================================================================

    private Workspace createWorkspace(Long id, String slug) {
        Workspace workspace = new Workspace();
        workspace.setId(id);
        workspace.setWorkspaceSlug(slug);
        workspace.setAccountLogin(slug);
        return workspace;
    }

    private User createSlackUser(String id, String name) {
        User user = new User();
        user.setId(id);
        user.setName(name);
        user.setRealName(name);
        Profile profile = new Profile();
        profile.setEmail(name + "@example.com");
        user.setProfile(profile);
        return user;
    }

    private LeaderboardEntryDTO createLeaderboardEntry(String login, String name) {
        UserInfoDTO userInfo = new UserInfoDTO(
            (long) login.hashCode(),
            login,
            name + "@example.com",
            "https://avatar.example.com/" + login,
            name,
            "https://github.com/" + login,
            0
        );
        return new LeaderboardEntryDTO(1, 100, userInfo, null, List.of(), 5, 3, 1, 10, 0, 0);
    }

    private void stubLeaderboardWithEntries(Workspace workspace, String... names) {
        List<LeaderboardEntryDTO> entries = java.util.Arrays.stream(names)
            .map(name -> createLeaderboardEntry(name, name))
            .toList();
        when(leaderboardService.createLeaderboard(eq(workspace), any(), any(), anyString(), any(), any())).thenReturn(
            entries
        );
    }

    private void stubSlackMembers(String... names) {
        List<User> slackUsers = java.util.Arrays.stream(names)
            .map(name -> createSlackUser("U_" + name.toUpperCase(), name))
            .toList();
        when(slackMessageService.getAllMembers()).thenReturn(slackUsers);
    }

    // ========================================================================
    // Tests: Early exit conditions
    // ========================================================================

    @Nested
    @DisplayName("run - early exit")
    class RunEarlyExitTests {

        @Test
        @DisplayName("returns early when slackMessageService is null")
        void returnsEarlyWhenServiceIsNull() {
            LeaderboardProperties properties = new LeaderboardProperties(
                new LeaderboardProperties.Schedule(1, "09:00"),
                new LeaderboardProperties.Notification(true, GLOBAL_TEAM, GLOBAL_CHANNEL_ID)
            );
            ApplicationProperties appProperties = new ApplicationProperties("https://hephaestus.example.com", null);
            SlackWeeklyLeaderboardTask nullServiceTask = new SlackWeeklyLeaderboardTask(
                properties,
                appProperties,
                null,
                leaderboardService,
                workspaceRepository
            );

            nullServiceTask.run();

            verifyNoInteractions(workspaceRepository);
        }

        @Test
        @DisplayName("returns early when no active workspaces exist")
        void returnsEarlyWhenNoActiveWorkspaces() {
            when(workspaceRepository.findByStatus(Workspace.WorkspaceStatus.ACTIVE)).thenReturn(List.of());

            task.run();

            verify(workspaceRepository).findByStatus(Workspace.WorkspaceStatus.ACTIVE);
            verifyNoInteractions(slackMessageService);
        }
    }

    // ========================================================================
    // Tests: Per-workspace notification routing
    // ========================================================================

    @Nested
    @DisplayName("run - per-workspace routing")
    class PerWorkspaceRoutingTests {

        @Test
        @DisplayName("uses workspace-specific channelId when configured")
        void usesWorkspaceChannelId() throws Exception {
            Workspace workspace = createWorkspace(1L, "ws-alpha");
            workspace.setLeaderboardNotificationEnabled(true);
            workspace.setLeaderboardNotificationChannelId("C_ALPHA");
            workspace.setLeaderboardNotificationTeam("alpha-team");

            when(workspaceRepository.findByStatus(Workspace.WorkspaceStatus.ACTIVE)).thenReturn(List.of(workspace));
            stubSlackMembers("alice");
            stubLeaderboardWithEntries(workspace, "alice");

            task.run();

            ArgumentCaptor<String> channelCaptor = ArgumentCaptor.forClass(String.class);
            verify(slackMessageService).sendMessage(channelCaptor.capture(), anyList(), anyString());
            assertThat(channelCaptor.getValue()).isEqualTo("C_ALPHA");
        }

        @Test
        @DisplayName("falls back to global channelId when workspace field is null")
        void fallsBackToGlobalChannelId() throws Exception {
            Workspace workspace = createWorkspace(1L, "ws-beta");
            // leaderboardNotificationChannelId is null -> fallback to global

            when(workspaceRepository.findByStatus(Workspace.WorkspaceStatus.ACTIVE)).thenReturn(List.of(workspace));
            stubSlackMembers("bob");
            stubLeaderboardWithEntries(workspace, "bob");

            task.run();

            ArgumentCaptor<String> channelCaptor = ArgumentCaptor.forClass(String.class);
            verify(slackMessageService).sendMessage(channelCaptor.capture(), anyList(), anyString());
            assertThat(channelCaptor.getValue()).isEqualTo(GLOBAL_CHANNEL_ID);
        }

        @Test
        @DisplayName("routes two workspaces to different channels")
        void routesTwoWorkspacesToDifferentChannels() throws Exception {
            Workspace ws1 = createWorkspace(1L, "ws-one");
            ws1.setLeaderboardNotificationEnabled(true);
            ws1.setLeaderboardNotificationChannelId("C_ONE");

            Workspace ws2 = createWorkspace(2L, "ws-two");
            ws2.setLeaderboardNotificationEnabled(true);
            ws2.setLeaderboardNotificationChannelId("C_TWO");

            when(workspaceRepository.findByStatus(Workspace.WorkspaceStatus.ACTIVE)).thenReturn(List.of(ws1, ws2));
            stubSlackMembers("alice", "bob");
            stubLeaderboardWithEntries(ws1, "alice");
            stubLeaderboardWithEntries(ws2, "bob");

            task.run();

            ArgumentCaptor<String> channelCaptor = ArgumentCaptor.forClass(String.class);
            verify(slackMessageService, times(2)).sendMessage(channelCaptor.capture(), anyList(), anyString());
            assertThat(channelCaptor.getAllValues()).containsExactly("C_ONE", "C_TWO");
        }

        @Test
        @DisplayName("uses workspace team when configured, global team as fallback")
        void usesWorkspaceTeamWithFallback() throws Exception {
            Workspace wsWithTeam = createWorkspace(1L, "ws-custom");
            wsWithTeam.setLeaderboardNotificationEnabled(true);
            wsWithTeam.setLeaderboardNotificationChannelId("C_CUSTOM");
            wsWithTeam.setLeaderboardNotificationTeam("custom-team");

            Workspace wsWithoutTeam = createWorkspace(2L, "ws-default");
            wsWithoutTeam.setLeaderboardNotificationEnabled(true);
            wsWithoutTeam.setLeaderboardNotificationChannelId("C_DEFAULT");
            // leaderboardNotificationTeam is null -> falls back to GLOBAL_TEAM

            when(workspaceRepository.findByStatus(Workspace.WorkspaceStatus.ACTIVE)).thenReturn(
                List.of(wsWithTeam, wsWithoutTeam)
            );
            stubSlackMembers("alice");
            stubLeaderboardWithEntries(wsWithTeam, "alice");
            stubLeaderboardWithEntries(wsWithoutTeam, "alice");

            task.run();

            // Verify the team filter passed to leaderboardService
            ArgumentCaptor<String> teamCaptor = ArgumentCaptor.forClass(String.class);
            verify(leaderboardService, times(2)).createLeaderboard(
                any(),
                any(),
                any(),
                teamCaptor.capture(),
                any(),
                any()
            );
            assertThat(teamCaptor.getAllValues()).containsExactly("custom-team", GLOBAL_TEAM);
        }
    }

    // ========================================================================
    // Tests: Workspace filtering
    // ========================================================================

    @Nested
    @DisplayName("run - workspace filtering")
    class WorkspaceFilteringTests {

        @Test
        @DisplayName("skips workspace with leaderboardNotificationEnabled=false")
        void skipsDisabledWorkspace() throws Exception {
            Workspace workspace = createWorkspace(1L, "ws-disabled");
            workspace.setLeaderboardNotificationEnabled(false);
            workspace.setLeaderboardNotificationChannelId("C_DISABLED");

            when(workspaceRepository.findByStatus(Workspace.WorkspaceStatus.ACTIVE)).thenReturn(List.of(workspace));
            stubSlackMembers("alice");

            task.run();

            verify(slackMessageService, never()).sendMessage(anyString(), anyList(), anyString());
            verifyNoInteractions(leaderboardService);
        }

        @Test
        @DisplayName("skips workspace with no channelId configured anywhere")
        void skipsWorkspaceWithNoChannel() throws Exception {
            // Build task with no global channelId either
            LeaderboardProperties noChannelProps = new LeaderboardProperties(
                new LeaderboardProperties.Schedule(1, "09:00"),
                new LeaderboardProperties.Notification(true, null, null)
            );
            ApplicationProperties appProperties = new ApplicationProperties("https://hephaestus.example.com", null);
            SlackWeeklyLeaderboardTask noChannelTask = new SlackWeeklyLeaderboardTask(
                noChannelProps,
                appProperties,
                slackMessageService,
                leaderboardService,
                workspaceRepository
            );

            Workspace workspace = createWorkspace(1L, "ws-no-channel");
            // No workspace channelId, no global channelId

            when(workspaceRepository.findByStatus(Workspace.WorkspaceStatus.ACTIVE)).thenReturn(List.of(workspace));
            stubSlackMembers("alice");

            noChannelTask.run();

            verify(slackMessageService, never()).sendMessage(anyString(), anyList(), anyString());
        }

        @Test
        @DisplayName("skips workspace with no qualified reviewers")
        void skipsWorkspaceWithNoReviewers() throws Exception {
            Workspace workspace = createWorkspace(1L, "ws-empty");
            workspace.setLeaderboardNotificationEnabled(true);
            workspace.setLeaderboardNotificationChannelId("C_EMPTY");

            when(workspaceRepository.findByStatus(Workspace.WorkspaceStatus.ACTIVE)).thenReturn(List.of(workspace));
            stubSlackMembers(); // no slack users -> no matches
            when(
                leaderboardService.createLeaderboard(eq(workspace), any(), any(), anyString(), any(), any())
            ).thenReturn(List.of());

            task.run();

            verify(slackMessageService, never()).sendMessage(anyString(), anyList(), anyString());
        }

        @Test
        @DisplayName("queries only ACTIVE workspaces, not all")
        void queriesOnlyActiveWorkspaces() {
            when(workspaceRepository.findByStatus(Workspace.WorkspaceStatus.ACTIVE)).thenReturn(List.of());

            task.run();

            verify(workspaceRepository).findByStatus(Workspace.WorkspaceStatus.ACTIVE);
            verify(workspaceRepository, never()).findAll();
        }
    }

    // ========================================================================
    // Tests: Performance
    // ========================================================================

    @Nested
    @DisplayName("run - performance")
    class PerformanceTests {

        @Test
        @DisplayName("fetches Slack members exactly once regardless of workspace count")
        void fetchesSlackMembersOnce() throws Exception {
            Workspace ws1 = createWorkspace(1L, "ws-one");
            ws1.setLeaderboardNotificationEnabled(true);
            ws1.setLeaderboardNotificationChannelId("C_ONE");

            Workspace ws2 = createWorkspace(2L, "ws-two");
            ws2.setLeaderboardNotificationEnabled(true);
            ws2.setLeaderboardNotificationChannelId("C_TWO");

            when(workspaceRepository.findByStatus(Workspace.WorkspaceStatus.ACTIVE)).thenReturn(List.of(ws1, ws2));
            stubSlackMembers("alice");
            stubLeaderboardWithEntries(ws1, "alice");
            stubLeaderboardWithEntries(ws2, "alice");

            task.run();

            verify(slackMessageService, times(1)).getAllMembers();
        }
    }

    // ========================================================================
    // Tests: Error resilience
    // ========================================================================

    @Nested
    @DisplayName("run - error handling")
    class ErrorHandlingTests {

        @Test
        @DisplayName("continues processing remaining workspaces when one fails")
        void continuesAfterFailure() throws Exception {
            Workspace ws1 = createWorkspace(1L, "ws-fail");
            ws1.setLeaderboardNotificationEnabled(true);
            ws1.setLeaderboardNotificationChannelId("C_FAIL");

            Workspace ws2 = createWorkspace(2L, "ws-ok");
            ws2.setLeaderboardNotificationEnabled(true);
            ws2.setLeaderboardNotificationChannelId("C_OK");

            when(workspaceRepository.findByStatus(Workspace.WorkspaceStatus.ACTIVE)).thenReturn(List.of(ws1, ws2));
            stubSlackMembers("alice");
            stubLeaderboardWithEntries(ws1, "alice");
            stubLeaderboardWithEntries(ws2, "alice");

            // First workspace send fails
            doThrow(new java.io.IOException("Slack API error"))
                .doNothing()
                .when(slackMessageService)
                .sendMessage(anyString(), anyList(), anyString());

            task.run();

            // Both workspaces attempted
            verify(slackMessageService, times(2)).sendMessage(anyString(), anyList(), anyString());
        }
    }

    // ========================================================================
    // Tests: Message content
    // ========================================================================

    @Nested
    @DisplayName("run - message content")
    class MessageContentTests {

        @Test
        @DisplayName("sends blocks containing workspace-specific leaderboard link")
        void sendsBlocksWithWorkspaceLink() throws Exception {
            Workspace workspace = createWorkspace(1L, "my-workspace");
            workspace.setLeaderboardNotificationEnabled(true);
            workspace.setLeaderboardNotificationChannelId("C_TEST");

            when(workspaceRepository.findByStatus(Workspace.WorkspaceStatus.ACTIVE)).thenReturn(List.of(workspace));
            stubSlackMembers("alice");
            stubLeaderboardWithEntries(workspace, "alice");

            task.run();

            ArgumentCaptor<List<LayoutBlock>> blocksCaptor = ArgumentCaptor.captor();
            verify(slackMessageService).sendMessage(
                eq("C_TEST"),
                blocksCaptor.capture(),
                eq("Weekly review highlights")
            );
            assertThat(blocksCaptor.getValue()).isNotEmpty();
        }
    }
}
