package de.tum.in.www1.hephaestus.leaderboard;

import static de.tum.in.www1.hephaestus.shared.LeaguePointsConstants.POINTS_DEFAULT;

import de.tum.in.www1.hephaestus.gitprovider.user.User;
import de.tum.in.www1.hephaestus.gitprovider.user.UserRepository;
import de.tum.in.www1.hephaestus.workspace.LeaguePointsRecalculator;
import de.tum.in.www1.hephaestus.workspace.Workspace;
import de.tum.in.www1.hephaestus.workspace.WorkspaceContributionActivityService;
import de.tum.in.www1.hephaestus.workspace.WorkspaceMembership;
import de.tum.in.www1.hephaestus.workspace.WorkspaceMembershipRepository;
import de.tum.in.www1.hephaestus.workspace.WorkspaceMembershipService;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Service responsible for recalculating league points for all members of a workspace.
 * This service iterates through historical contribution windows and updates each member's
 * league points based on their leaderboard performance in each window.
 *
 * <p>Implements {@link LeaguePointsRecalculator} to allow the workspace module to trigger
 * recalculation without creating a cyclic dependency.
 */
@Service
public class LeaguePointsRecalculationService implements LeaguePointsRecalculator {

    private static final Logger logger = LoggerFactory.getLogger(LeaguePointsRecalculationService.class);

    private final WorkspaceMembershipRepository workspaceMembershipRepository;
    private final WorkspaceMembershipService workspaceMembershipService;
    private final WorkspaceContributionActivityService workspaceContributionActivityService;
    private final LeaderboardService leaderboardService;
    private final LeaguePointsCalculationService leaguePointsCalculationService;
    private final UserRepository userRepository;

    public LeaguePointsRecalculationService(
        WorkspaceMembershipRepository workspaceMembershipRepository,
        WorkspaceMembershipService workspaceMembershipService,
        WorkspaceContributionActivityService workspaceContributionActivityService,
        LeaderboardService leaderboardService,
        LeaguePointsCalculationService leaguePointsCalculationService,
        UserRepository userRepository
    ) {
        this.workspaceMembershipRepository = workspaceMembershipRepository;
        this.workspaceMembershipService = workspaceMembershipService;
        this.workspaceContributionActivityService = workspaceContributionActivityService;
        this.leaderboardService = leaderboardService;
        this.leaguePointsCalculationService = leaguePointsCalculationService;
        this.userRepository = userRepository;
    }

    @Override
    public void recalculate(Workspace workspace) {
        if (workspace == null || workspace.getId() == null) {
            logger.warn("Skipping league recalculation because workspace is missing or not persisted");
            return;
        }

        Long workspaceId = workspace.getId();
        logger.info("Recalculating league points per member for workspace id={}", workspaceId);

        workspaceMembershipService.resetLeaguePoints(workspaceId, POINTS_DEFAULT);

        List<WorkspaceMembership> memberships = workspaceMembershipRepository.findAllWithUserByWorkspaceId(workspaceId);
        if (memberships.isEmpty()) {
            logger.info("Workspace id={} has no memberships; nothing to recalculate", workspaceId);
            return;
        }

        Map<Long, User> memberUsersById = new HashMap<>();
        Map<Long, Integer> currentPointsByUserId = new HashMap<>();
        Map<Long, Instant> firstContributionByUserId = new HashMap<>();

        memberships.forEach(membership -> {
            User memberUser = membership.getUser();
            if (!isProcessableUser(memberUser)) {
                return;
            }

            Long userId = memberUser.getId();
            User hydratedUser = userRepository
                .findByLoginWithEagerMergedPullRequests(memberUser.getLogin())
                .orElse(memberUser);
            memberUsersById.put(userId, hydratedUser);
            currentPointsByUserId.put(userId, POINTS_DEFAULT);
            Instant firstContribution = workspaceContributionActivityService
                .findFirstContributionInstant(workspaceId, userId)
                .orElse(null);
            firstContributionByUserId.put(userId, firstContribution);
        });

        if (memberUsersById.isEmpty()) {
            logger.info("Workspace id={} has no eligible members for recalculation", workspaceId);
            return;
        }

        Instant earliestContribution = firstContributionByUserId
            .values()
            .stream()
            .filter(Objects::nonNull)
            .min(Instant::compareTo)
            .orElse(null);

        if (earliestContribution == null) {
            logger.info("Workspace id={} has no contributions; league points remain at default", workspaceId);
            return;
        }

        Instant recalculationAnchor = Instant.now();
        Instant windowStart = earliestContribution;

        while (windowStart.isBefore(recalculationAnchor)) {
            Instant windowEnd = windowStart.plus(7, ChronoUnit.DAYS);
            if (windowEnd.isAfter(recalculationAnchor)) {
                windowEnd = recalculationAnchor;
            }

            List<LeaderboardEntryDTO> leaderboardEntries = leaderboardService.createLeaderboard(
                workspace,
                windowStart,
                windowEnd,
                "all",
                LeaderboardSortType.SCORE,
                LeaderboardMode.INDIVIDUAL
            );

            Instant windowEndSnapshot = windowEnd;
            leaderboardEntries.forEach(entry ->
                updateMemberPointsForEntry(
                    workspaceId,
                    entry,
                    memberUsersById,
                    firstContributionByUserId,
                    currentPointsByUserId,
                    windowEndSnapshot
                )
            );

            if (!windowEnd.isAfter(windowStart)) {
                break;
            }

            windowStart = windowEnd;
        }

        logger.info("Finished recalculating league points for workspace id={}", workspaceId);
    }

    private void updateMemberPointsForEntry(
        Long workspaceId,
        LeaderboardEntryDTO entry,
        Map<Long, User> memberUsersById,
        Map<Long, Instant> firstContributionByUserId,
        Map<Long, Integer> currentPointsByUserId,
        Instant windowEnd
    ) {
        if (entry == null || entry.user() == null || entry.user().id() == null) {
            return;
        }

        Long userId = entry.user().id();
        User memberUser = memberUsersById.get(userId);
        if (memberUser == null) {
            return;
        }

        Instant firstContribution = firstContributionByUserId.get(userId);
        if (firstContribution == null || !windowEnd.isAfter(firstContribution)) {
            return;
        }

        int currentPoints = currentPointsByUserId.getOrDefault(userId, POINTS_DEFAULT);
        int newPoints = leaguePointsCalculationService.calculateNewPoints(memberUser, currentPoints, entry);
        currentPointsByUserId.put(userId, newPoints);
        workspaceMembershipService.updateLeaguePoints(workspaceId, memberUser, newPoints);
    }

    private boolean isProcessableUser(User user) {
        return user != null && user.getId() != null && user.getLogin() != null && user.getType() == User.Type.USER;
    }
}
