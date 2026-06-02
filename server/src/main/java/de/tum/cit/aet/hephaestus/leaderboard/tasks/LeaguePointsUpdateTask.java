package de.tum.cit.aet.hephaestus.leaderboard.tasks;

import de.tum.cit.aet.hephaestus.integration.scm.domain.user.UserInfoDTO;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.UserRepository;
import de.tum.cit.aet.hephaestus.leaderboard.*;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import de.tum.cit.aet.hephaestus.workspace.WorkspaceMembershipService;
import de.tum.cit.aet.hephaestus.workspace.WorkspaceRepository;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.List;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Recomputes league points for a single workspace from its just-closed leaderboard cycle.
 *
 * <p>Per-workspace: the {@code LeaderboardTaskScheduler} invokes {@link #runForWorkspace(Workspace)}
 * on each workspace's own cron tick, so the cycle window is resolved from that workspace's schedule
 * (via {@link LeaderboardScheduleResolver}), not a single global cron.
 */
@Component
public class LeaguePointsUpdateTask {

    private static final Logger log = LoggerFactory.getLogger(LeaguePointsUpdateTask.class);

    private final UserRepository userRepository;
    private final LeaderboardService leaderboardService;
    private final LeaguePointsService leaguePointsService;
    private final WorkspaceMembershipService workspaceMembershipService;
    private final LeaderboardScheduleResolver scheduleResolver;
    private final WorkspaceRepository workspaceRepository;

    public LeaguePointsUpdateTask(
        UserRepository userRepository,
        LeaderboardService leaderboardService,
        LeaguePointsService leaguePointsService,
        WorkspaceMembershipService workspaceMembershipService,
        LeaderboardScheduleResolver scheduleResolver,
        WorkspaceRepository workspaceRepository
    ) {
        this.userRepository = userRepository;
        this.leaderboardService = leaderboardService;
        this.leaguePointsService = leaguePointsService;
        this.workspaceMembershipService = workspaceMembershipService;
        this.scheduleResolver = scheduleResolver;
        this.workspaceRepository = workspaceRepository;
    }

    /**
     * Update league points for every member of {@code workspace}, scored against the workspace's
     * just-closed leaderboard cycle.
     *
     * <p>Idempotent per cycle: the scheduler passes a detached entity, so we re-load the managed row
     * inside this transaction, then skip if this cycle's points were already applied (its end instant
     * recorded in {@code leaderboardLeagueCycleAt}). Because scoring accumulates, this guard is what
     * keeps a lock-expiry retry or manual replay from double-awarding.
     */
    @Transactional
    public void runForWorkspace(Workspace workspace) {
        if (workspace == null || workspace.getId() == null) {
            log.warn("Skipped league points update: reason=missingWorkspaceId");
            return;
        }
        Long workspaceId = workspace.getId();
        Workspace managed = workspaceRepository.findById(workspaceId).orElse(null);
        if (managed == null) {
            return;
        }

        LeaderboardScheduleResolver.CycleWindow window = scheduleResolver.previousCycleWindow(managed);
        Instant lastCycle = managed.getLeaderboardLeagueCycleAt();
        if (lastCycle != null && !window.before().isAfter(lastCycle)) {
            log.debug(
                "Skipped league points update: reason=cycleAlreadyApplied, workspaceId={}, cycleEnd={}",
                workspaceId,
                window.before()
            );
            return;
        }

        log.debug("Started league points update: workspaceId={}", workspaceId);
        List<LeaderboardEntryDTO> leaderboard = leaderboardService.createLeaderboard(
            managed,
            window.after(),
            window.before(),
            "all",
            LeaderboardSortType.SCORE,
            LeaderboardMode.INDIVIDUAL
        );
        leaderboard.forEach(updateLeaderboardEntry(workspaceId));
        managed.setLeaderboardLeagueCycleAt(window.before());

        log.debug("Updated league points: workspaceId={}, userCount={}", workspaceId, leaderboard.size());
    }

    /**
     * Update ranking points of a user based on its leaderboard entry.
     *
     * @return {@code Consumer} that updates {@code leaguePoints} based on its leaderboard entry.
     */
    private Consumer<? super LeaderboardEntryDTO> updateLeaderboardEntry(Long workspaceId) {
        return entry -> {
            UserInfoDTO leaderboardUser = entry.user();
            if (leaderboardUser == null) {
                return;
            }
            var user = userRepository.findByLoginWithEagerMergedPullRequests(leaderboardUser.login()).orElseThrow();
            int currentPoints = workspaceMembershipService.getCurrentLeaguePoints(workspaceId, user);
            int newPoints = leaguePointsService.calculateNewPoints(user, currentPoints, entry);
            workspaceMembershipService.updateLeaguePoints(workspaceId, user, newPoints);
        };
    }
}
