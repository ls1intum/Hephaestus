package de.tum.in.www1.hephaestus.leaderboard.tasks;

import de.tum.in.www1.hephaestus.gitprovider.user.UserInfoDTO;
import de.tum.in.www1.hephaestus.gitprovider.user.UserRepository;
import de.tum.in.www1.hephaestus.leaderboard.*;
import de.tum.in.www1.hephaestus.workspace.Workspace;
import de.tum.in.www1.hephaestus.workspace.WorkspaceMembershipService;
import de.tum.in.www1.hephaestus.workspace.WorkspaceRepository;
import de.tum.in.www1.hephaestus.workspace.context.WorkspaceContext;
import de.tum.in.www1.hephaestus.workspace.context.WorkspaceContextHolder;
import jakarta.transaction.Transactional;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class LeaguePointsUpdateTask implements Runnable {

    private static final Logger logger = LoggerFactory.getLogger(LeaguePointsUpdateTask.class);

    @Value("${hephaestus.leaderboard.schedule.day}")
    private String scheduledDay;

    @Value("${hephaestus.leaderboard.schedule.time}")
    private String scheduledTime;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private LeaderboardService leaderboardService;

    @Autowired
    private LeaguePointsCalculationService leaguePointsCalculationService;

    @Autowired
    private WorkspaceMembershipService workspaceMembershipService;

    @Autowired
    private WorkspaceRepository workspaceRepository;

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
            WorkspaceContext context = WorkspaceContext.fromWorkspace(workspace, Set.of());
            WorkspaceContextHolder.setContext(context);

            try {
                updateLeaguePointsForWorkspace(workspace.getId());
            } catch (Exception e) {
                logger.error(
                    "Failed to update league points for workspace '{}' (id={}): {}",
                    workspace.getWorkspaceSlug(),
                    workspace.getId(),
                    e.getMessage(),
                    e
                );
            } finally {
                WorkspaceContextHolder.clearContext();
            }
        }

        logger.info("Completed scheduled league points update for {} workspace(s).", workspaces.size());
    }

    /**
     * Updates league points for all members of a specific workspace based on the latest leaderboard.
     *
     * @param workspaceId the workspace ID for which to update league points
     */
    private void updateLeaguePointsForWorkspace(Long workspaceId) {
        logger.debug("Updating league points for workspace id={}.", workspaceId);

        List<LeaderboardEntryDTO> leaderboard = getLatestLeaderboard();
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
            int newPoints = leaguePointsCalculationService.calculateNewPoints(user, currentPoints, entry);
            workspaceMembershipService.updateLeaguePoints(workspaceId, user, newPoints);
        };
    }

    /**
     * Retrieves the latest leaderboard based on the scheduled time of the environment.
     *
     * @return List of {@code LeaderboardEntryDTO} representing the latest leaderboard
     */
    private List<LeaderboardEntryDTO> getLatestLeaderboard() {
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
            after,
            before,
            "all",
            LeaderboardSortType.SCORE,
            LeaderboardMode.INDIVIDUAL
        );
    }
}
