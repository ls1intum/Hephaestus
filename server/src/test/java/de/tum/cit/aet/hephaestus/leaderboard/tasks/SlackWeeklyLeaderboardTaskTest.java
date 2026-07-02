package de.tum.cit.aet.hephaestus.leaderboard.tasks;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.config.ApplicationProperties;
import de.tum.cit.aet.hephaestus.integration.core.connection.ConnectionConfig;
import de.tum.cit.aet.hephaestus.integration.core.connection.ConnectionService;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.UserInfoDTO;
import de.tum.cit.aet.hephaestus.leaderboard.LeaderboardEntryDTO;
import de.tum.cit.aet.hephaestus.leaderboard.LeaderboardMode;
import de.tum.cit.aet.hephaestus.leaderboard.LeaderboardScheduleResolver;
import de.tum.cit.aet.hephaestus.leaderboard.LeaderboardService;
import de.tum.cit.aet.hephaestus.leaderboard.LeaderboardSortType;
import de.tum.cit.aet.hephaestus.leaderboard.spi.LeaderboardDigestReadyEvent;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.springframework.context.ApplicationEventPublisher;

/**
 * Verifies the leaderboard-side half of the weekly Slack digest, now invoked <b>per workspace</b>
 * by the scheduler via {@link SlackWeeklyLeaderboardTask#runForWorkspace}. These tests pin the
 * skip preconditions that gate event emission (notifications disabled, no active Slack connection,
 * no channel configured, no entries) and the happy-path event shape. Cross-workspace iteration +
 * failure isolation now live in {@code LeaderboardTaskScheduler}, not here.
 */
class SlackWeeklyLeaderboardTaskTest extends BaseUnitTest {

    @Mock
    private LeaderboardService leaderboardService;

    @Mock
    private ConnectionService connectionService;

    @Mock
    private LeaderboardScheduleResolver scheduleResolver;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private SlackWeeklyLeaderboardTask task;

    @BeforeEach
    void setUp() {
        ApplicationProperties appProps = new ApplicationProperties(
            "https://app.test",
            new ApplicationProperties.Webapp("https://app.test")
        );
        task = new SlackWeeklyLeaderboardTask(
            appProps,
            leaderboardService,
            connectionService,
            scheduleResolver,
            eventPublisher
        );
        Mockito.lenient()
            .when(scheduleResolver.previousCycleWindow(any(Workspace.class)))
            .thenReturn(
                new LeaderboardScheduleResolver.CycleWindow(
                    Instant.parse("2026-01-01T00:00:00Z"),
                    Instant.parse("2026-01-08T00:00:00Z")
                )
            );
    }

    @Test
    void noActiveSlackConnection_skipsWithoutPublishingEvent() {
        Workspace w = workspace(99L, "acme", true);
        when(connectionService.findSlackNotificationConfig(99L)).thenReturn(Optional.empty());

        task.runForWorkspace(w);

        verify(eventPublisher, never()).publishEvent(any());
    }

    /** Per-workspace skip preconditions all route through a fast-return path: skip → no event. */
    @ParameterizedTest(name = "{0}")
    @MethodSource("skipScenarios")
    void skipConditions_neverPublishEvent(String name, Workspace w, Optional<ConnectionConfig.SlackConfig> cfg) {
        Mockito.lenient().when(connectionService.findSlackNotificationConfig(w.getId())).thenReturn(cfg);

        task.runForWorkspace(w);

        verify(eventPublisher, never()).publishEvent(any());
    }

    private static Stream<Arguments> skipScenarios() {
        return Stream.of(
            Arguments.of(
                "notificationsDisabled",
                workspace(1L, "acme", false),
                Optional.of(slackConfig("C0974LJBPBK", "engineering"))
            ),
            Arguments.of("noChannelConfigured", workspace(1L, "acme", true), Optional.of(slackConfig(null, null)))
        );
    }

    @Test
    void emptyLeaderboard_doesNotPublishEvent() {
        Workspace w = workspace(99L, "acme", true);
        when(connectionService.findSlackNotificationConfig(99L)).thenReturn(
            Optional.of(slackConfig("C0974LJBPBK", null))
        );
        when(
            leaderboardService.createLeaderboard(
                any(Workspace.class),
                any(),
                any(),
                anyString(),
                eq(LeaderboardSortType.SCORE),
                eq(LeaderboardMode.INDIVIDUAL)
            )
        ).thenReturn(List.of());

        task.runForWorkspace(w);

        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void happyPath_publishesOneEventForTheWorkspace() {
        Workspace w = workspace(99L, "acme", true);
        when(connectionService.findSlackNotificationConfig(99L)).thenReturn(
            Optional.of(slackConfig("C0974LJBPBK", "engineering"))
        );
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

        task.runForWorkspace(w);

        ArgumentCaptor<LeaderboardDigestReadyEvent> captor = ArgumentCaptor.forClass(LeaderboardDigestReadyEvent.class);
        verify(eventPublisher, times(1)).publishEvent(captor.capture());
        LeaderboardDigestReadyEvent event = captor.getValue();
        assertThat(event.workspaceId()).isEqualTo(99L);
        assertThat(event.workspaceSlug()).isEqualTo("acme");
        assertThat(event.channelId()).isEqualTo("C0974LJBPBK");
        assertThat(event.teamLabel()).isEqualTo("engineering");
        assertThat(event.topEntries()).hasSize(1);
        assertThat(event.baseUrl()).isEqualTo("https://app.test");
    }

    // Test helpers

    private static Workspace workspace(long id, String slug, boolean notificationsEnabled) {
        Workspace w = new Workspace();
        w.setId(id);
        w.setWorkspaceSlug(slug);
        w.setLeaderboardNotificationEnabled(notificationsEnabled);
        return w;
    }

    private static ConnectionConfig.SlackConfig slackConfig(String channelId, String teamLabel) {
        return new ConnectionConfig.SlackConfig("T1", "Acme", channelId, teamLabel, null, Set.of());
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
        return new LeaderboardEntryDTO(1, 10, user, null, List.of(), 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
    }
}
