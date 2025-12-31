package de.tum.in.www1.hephaestus.leaderboard;

import static java.util.function.Function.identity;
import static java.util.stream.Collectors.*;

import de.tum.in.www1.hephaestus.activity.ActivityBreakdownProjection;
import de.tum.in.www1.hephaestus.activity.ActivityEventRepository;
import de.tum.in.www1.hephaestus.activity.ActivityXpProjection;
import de.tum.in.www1.hephaestus.gitprovider.user.User;
import de.tum.in.www1.hephaestus.gitprovider.user.UserRepository;
import java.time.Instant;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Queries leaderboard XP data from the activity event ledger.
 *
 * <p>The leaderboard reads pre-computed XP from activity events rather than
 * recalculating on-the-fly. This is the <strong>single source of truth</strong> for XP.
 *
 * <p>Architecture:
 * <pre>
 * Domain Events → ActivityEventListener → ActivityEvent table (activity module)
 *                                                ↓
 *                        LeaderboardXpQueryService (this) ← LeaderboardService
 * </pre>
 */
@Service
public class LeaderboardXpQueryService {

    private static final Logger logger = LoggerFactory.getLogger(LeaderboardXpQueryService.class);

    private final ActivityEventRepository activityEventRepository;
    private final UserRepository userRepository;

    public LeaderboardXpQueryService(ActivityEventRepository activityEventRepository, UserRepository userRepository) {
        this.activityEventRepository = activityEventRepository;
        this.userRepository = userRepository;
    }

    /**
     * Get XP totals for all actors in a workspace timeframe.
     *
     * @param workspaceId the workspace
     * @param since start of timeframe (inclusive)
     * @param until end of timeframe (exclusive)
     * @return map of actor ID to XP data
     */
    @Transactional(readOnly = true)
    public Map<Long, LeaderboardUserXp> getLeaderboardData(Long workspaceId, Instant since, Instant until) {
        return getLeaderboardData(workspaceId, since, until, Set.of());
    }

    /**
     * Get XP totals for actors in specific teams.
     *
     * @param workspaceId the workspace
     * @param since start of timeframe (inclusive)
     * @param until end of timeframe (exclusive)
     * @param teamIds team IDs to filter by (empty = all teams)
     * @return map of actor ID to XP data
     */
    @Transactional(readOnly = true)
    public Map<Long, LeaderboardUserXp> getLeaderboardData(
        Long workspaceId,
        Instant since,
        Instant until,
        Set<Long> teamIds
    ) {
        logger.debug(
            "Fetching leaderboard data: workspaceId={}, since={}, until={}, teamIds={}",
            workspaceId,
            since,
            until,
            teamIds.isEmpty() ? "all" : teamIds
        );

        // 1. Get XP totals from activity_event table
        List<ActivityXpProjection> xpData;
        if (teamIds.isEmpty()) {
            xpData = activityEventRepository.findExperiencePointsByWorkspaceAndTimeframe(workspaceId, since, until);
        } else {
            xpData = activityEventRepository.findExperiencePointsByWorkspaceAndTeamsAndTimeframe(
                workspaceId,
                teamIds,
                since,
                until
            );
        }

        if (xpData.isEmpty()) {
            logger.debug("No activity events found for leaderboard");
            return Map.of();
        }

        // 2. Get actor IDs for breakdown query
        Set<Long> actorIds = xpData.stream().map(ActivityXpProjection::getActorId).collect(toSet());

        // 3. Get activity breakdown by type
        List<ActivityBreakdownProjection> breakdown = activityEventRepository.findActivityBreakdown(
            workspaceId,
            actorIds,
            since,
            until
        );

        // 4. Hydrate user data
        Map<Long, User> usersById = userRepository
            .findAllById(actorIds)
            .stream()
            .collect(toMap(User::getId, identity(), (a, b) -> a));

        // 5. Build result map using builders for incremental construction
        Map<Long, LeaderboardUserXp.Builder> builders = new HashMap<>();

        for (ActivityXpProjection xp : xpData) {
            Long actorId = xp.getActorId();
            User user = usersById.get(actorId);
            if (user == null) {
                logger.warn("User not found for actor ID: {}", actorId);
                continue;
            }

            int totalScore = xp.getTotalExperiencePoints() != null ? xp.getTotalExperiencePoints().intValue() : 0;
            int eventCount = xp.getEventCount() != null ? xp.getEventCount().intValue() : 0;

            builders.put(actorId, new LeaderboardUserXp.Builder(user, totalScore, eventCount));
        }

        // 6. Enrich builders with breakdown stats
        for (ActivityBreakdownProjection stat : breakdown) {
            Long actorId = stat.getActorId();
            LeaderboardUserXp.Builder builder = builders.get(actorId);
            if (builder == null) {
                continue;
            }

            int count = stat.getCount() != null ? stat.getCount().intValue() : 0;

            switch (stat.getEventType()) {
                case REVIEW_APPROVED -> builder.addApprovals(count);
                case REVIEW_CHANGES_REQUESTED -> builder.addChangeRequests(count);
                case REVIEW_COMMENTED -> builder.addComments(count);
                case COMMENT_CREATED -> builder.addIssueComments(count);
                case REVIEW_COMMENT_CREATED -> builder.addCodeComments(count);
                default -> {
                    // PR events don't contribute to review stats
                }
            }
        }

        // 7. Build immutable results
        Map<Long, LeaderboardUserXp> result = builders
            .entrySet()
            .stream()
            .collect(toMap(Map.Entry::getKey, e -> e.getValue().build()));

        logger.debug("Built leaderboard data for {} users", result.size());
        return result;
    }
}
