package de.tum.cit.aet.hephaestus.leaderboard.tasks;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.integration.scm.domain.user.User;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.UserInfoDTO;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.UserRepository;
import de.tum.cit.aet.hephaestus.leaderboard.LeaderboardEntryDTO;
import de.tum.cit.aet.hephaestus.leaderboard.LeaderboardMode;
import de.tum.cit.aet.hephaestus.leaderboard.LeaderboardScheduleResolver;
import de.tum.cit.aet.hephaestus.leaderboard.LeaderboardService;
import de.tum.cit.aet.hephaestus.leaderboard.LeaderboardSortType;
import de.tum.cit.aet.hephaestus.leaderboard.LeaguePointsService;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import de.tum.cit.aet.hephaestus.workspace.WorkspaceMembershipService;
import de.tum.cit.aet.hephaestus.workspace.WorkspaceRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.Mockito;

/**
 * Pins the per-cycle idempotency guard: league points accumulate ({@code newPoints = current +
 * delta}), so the task must apply a given cycle at most once. A re-run for an already-recorded
 * cycle (lock expiry, manual replay, at-least-once delivery) must no-op rather than double-award.
 */
class LeaguePointsUpdateTaskTest extends BaseUnitTest {

    private static final Instant CYCLE_AFTER = Instant.parse("2026-01-01T00:00:00Z");
    private static final Instant CYCLE_BEFORE = Instant.parse("2026-01-08T09:00:00Z");

    @Mock
    private UserRepository userRepository;

    @Mock
    private LeaderboardService leaderboardService;

    @Mock
    private LeaguePointsService leaguePointsService;

    @Mock
    private WorkspaceMembershipService workspaceMembershipService;

    @Mock
    private LeaderboardScheduleResolver scheduleResolver;

    @Mock
    private WorkspaceRepository workspaceRepository;

    private LeaguePointsUpdateTask task;

    @BeforeEach
    void setUp() {
        task = new LeaguePointsUpdateTask(
            userRepository,
            leaderboardService,
            leaguePointsService,
            workspaceMembershipService,
            scheduleResolver,
            workspaceRepository
        );
        Mockito.lenient()
            .when(scheduleResolver.previousCycleWindow(any(Workspace.class)))
            .thenReturn(new LeaderboardScheduleResolver.CycleWindow(CYCLE_AFTER, CYCLE_BEFORE));
    }

    @Test
    void freshCycle_appliesPointsAndRecordsCycleMarker() {
        Workspace managed = workspace(7L, null);
        stubManaged(managed);
        stubLeaderboardWith("alice");
        User alice = stubUser("alice");
        when(workspaceMembershipService.getCurrentLeaguePoints(7L, alice)).thenReturn(100);
        when(leaguePointsService.calculateNewPoints(eq(alice), eq(100), any())).thenReturn(110);

        task.runForWorkspace(workspace(7L, null));

        verify(workspaceMembershipService).updateLeaguePoints(7L, alice, 110);
        assertThat(managed.getLeaderboardLeagueCycleAt()).isEqualTo(CYCLE_BEFORE);
    }

    @Test
    void cycleAlreadyApplied_skipsWithoutTouchingPoints() {
        Workspace managed = workspace(7L, CYCLE_BEFORE);
        stubManaged(managed);

        task.runForWorkspace(workspace(7L, CYCLE_BEFORE));

        verify(leaderboardService, never()).createLeaderboard(any(), any(), any(), anyString(), any(), any());
        verify(workspaceMembershipService, never()).updateLeaguePoints(any(), any(), anyInt());
    }

    @Test
    void markerFromEarlierCycle_appliesTheNewCycle() {
        Workspace managed = workspace(7L, CYCLE_AFTER.minusSeconds(1));
        stubManaged(managed);
        stubLeaderboardWith("bob");
        User bob = stubUser("bob");
        when(workspaceMembershipService.getCurrentLeaguePoints(7L, bob)).thenReturn(50);
        when(leaguePointsService.calculateNewPoints(eq(bob), eq(50), any())).thenReturn(60);

        task.runForWorkspace(workspace(7L, CYCLE_AFTER.minusSeconds(1)));

        verify(workspaceMembershipService).updateLeaguePoints(7L, bob, 60);
        assertThat(managed.getLeaderboardLeagueCycleAt()).isEqualTo(CYCLE_BEFORE);
    }

    // Test helpers

    private void stubManaged(Workspace managed) {
        when(workspaceRepository.findById(managed.getId())).thenReturn(Optional.of(managed));
    }

    private void stubLeaderboardWith(String login) {
        when(
            leaderboardService.createLeaderboard(
                any(Workspace.class),
                eq(CYCLE_AFTER),
                eq(CYCLE_BEFORE),
                eq("all"),
                eq(LeaderboardSortType.SCORE),
                eq(LeaderboardMode.INDIVIDUAL)
            )
        ).thenReturn(List.of(entry(login)));
    }

    private User stubUser(String login) {
        User user = new User();
        when(userRepository.findByLoginWithEagerMergedPullRequests(login)).thenReturn(Optional.of(user));
        return user;
    }

    private static Workspace workspace(long id, Instant lastCycle) {
        Workspace w = new Workspace();
        w.setId(id);
        w.setLeaderboardLeagueCycleAt(lastCycle);
        return w;
    }

    private static LeaderboardEntryDTO entry(String login) {
        UserInfoDTO user = new UserInfoDTO(
            1L,
            login,
            login + "@example.com",
            "https://example.com/a.png",
            login,
            "https://example.com/" + login,
            0
        );
        return new LeaderboardEntryDTO(1, 10, user, null, List.of(), 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
    }
}
