package de.tum.in.www1.hephaestus.leaderboard.tasks;

import de.tum.in.www1.hephaestus.gitprovider.user.UserInfoDTO;
import de.tum.in.www1.hephaestus.gitprovider.user.UserRepository;
import de.tum.in.www1.hephaestus.leaderboard.*;
import de.tum.in.www1.hephaestus.workspace.Workspace;
import de.tum.in.www1.hephaestus.workspace.WorkspaceMembershipService;
import de.tum.in.www1.hephaestus.workspace.WorkspaceRepository;
import jakarta.transaction.Transactional;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class LeaguePointsUpdateTask implements Runnable {

    private static final Logger logger = LoggerFactory.getLogger(LeaguePointsUpdateTask.class);

    private final String scheduledDay;
    private final String scheduledTime;
    private final UserRepository userRepository;
    private final LeaderboardService leaderboardService;
    private final LeaguePointsService leaguePointsService;
    private final WorkspaceMembershipService workspaceMembershipService;
    private final WorkspaceRepository workspaceRepository;

    public LeaguePointsUpdateTask(
        @Value("${hephaestus.leaderboard.schedule.day}") String scheduledDay,
        @Value("${hephaestus.leaderboard.schedule.time}") String scheduledTime,
        UserRepository userRepository,
        LeaderboardService leaderboardService,
        LeaguePointsService leaguePointsService,
        WorkspaceMembershipService workspaceMembershipService,
        WorkspaceRepository workspaceRepository
    ) {
        this.scheduledDay = scheduledDay;
        this.scheduledTime = scheduledTime;
        this.userRepository = userRepository;
        this.leaderboardService = leaderboardService;
        this.leaguePointsService = leaguePointsService;
        this.workspaceMembershipService = workspaceMembershipService;
        this.workspaceRepository = workspaceRepository;
    }

    @Override
    @Transactional
    public void run() {
        List<Workspace> workspaces = workspaceRepository.findAll();

        if (workspaces.isEmpty()) {
            logger.debug("Skipping league points update because no workspaces are configured.");
            return;
        }

        logger.info("Starting scheduled league points update for {} workspace(s).", workspaces.size());

        for (Workspace workspace : workspaces) {
            try {
                updateLeaguePointsForWorkspace(workspace);
            } catch (Exception e) {
                logger.error(
                    "Failed to update league points for workspace '{}' (id={}): {}",
                    workspace.getWorkspaceSlug(),
                    workspace.getId(),
                    e.getMessage(),
                    e
                );
            }
        }

        logger.info("Completed scheduled league points update for {} workspace(s).", workspaces.size());
    }

    /**
     * Updates league points for all members of a specific workspace based on the latest leaderboard.
     *
     * @param workspaceId the workspace ID for which to update league points
     */
    private void updateLeaguePointsForWorkspace(Workspace workspace) {
        if (workspace == null || workspace.getId() == null) {
            logger.warn("Skipping league points update because workspace context is missing an id");
            return;
        }

        Long workspaceId = workspace.getId();
        logger.debug("Updating league points for workspace id={}.", workspaceId);

        List<LeaderboardEntryDTO> leaderboard = getLatestLeaderboard(workspace);
        leaderboard.forEach(updateLeaderboardEntry(workspaceId));

        logger.debug("Updated league points for {} users in workspace id={}.", leaderboard.size(), workspaceId);
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

    /**
     * Retrieves the latest leaderboard based on the scheduled time of the environment.
     *
     * @return List of {@code LeaderboardEntryDTO} representing the latest leaderboard
     */
    private List<LeaderboardEntryDTO> getLatestLeaderboard(Workspace workspace) {
        String[] timeParts = scheduledTime.split(":");
        ZonedDateTime zonedNow = ZonedDateTime.now(ZoneId.systemDefault());
        ZonedDateTime zonedBefore = zonedNow
            .with(TemporalAdjusters.previousOrSame(DayOfWeek.of(Integer.parseInt(scheduledDay))))
            .withHour(Integer.parseInt(timeParts[0]))
            .withMinute(timeParts.length > 1 ? Integer.parseInt(timeParts[1]) : 0)
            .withSecond(0)
            .withNano(0);
        Instant before = zonedBefore.toInstant();
        Instant after = zonedBefore.minusWeeks(1).toInstant();
        return leaderboardService.createLeaderboard(
            workspace,
            after,
            before,
            "all",
            LeaderboardSortType.SCORE,
            LeaderboardMode.INDIVIDUAL
        );
    }
}
