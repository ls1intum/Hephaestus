package de.tum.in.www1.hephaestus.leaderboard;

import static java.util.function.Function.identity;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toSet;

import de.tum.in.www1.hephaestus.activity.ActivityBreakdownProjection;
import de.tum.in.www1.hephaestus.activity.ActivityEventRepository;
import de.tum.in.www1.hephaestus.activity.ActivityXpProjection;
import de.tum.in.www1.hephaestus.activity.scoring.XpPrecision;
import de.tum.in.www1.hephaestus.gitprovider.user.User;
import de.tum.in.www1.hephaestus.gitprovider.user.UserRepository;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
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

    private static final Logger log = LoggerFactory.getLogger(LeaderboardXpQueryService.class);

    private final ActivityEventRepository activityEventRepository;
    private final UserRepository userRepository;

    public LeaderboardXpQueryService(ActivityEventRepository activityEventRepository, UserRepository userRepository) {
        this.activityEventRepository = activityEventRepository;
        this.userRepository = userRepository;
    }

    /**
     * Builds a deterministic cache key from team IDs.
     *
     * <p>This method is called via SpEL from the @Cacheable annotation to ensure
     * consistent cache keys regardless of Set implementation.
     *
     * @param teamIds the team IDs to create a key from
     * @return sorted, comma-joined string of team IDs (empty string if empty)
     */
    public String buildTeamIdsCacheKey(Set<Long> teamIds) {
        if (teamIds == null || teamIds.isEmpty()) {
            return "";
        }
        return teamIds.stream().sorted().map(String::valueOf).collect(joining(","));
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
     * <p>Results are cached for 60 seconds (TTL) to reduce database load on hot leaderboard queries.
     * The cache is also eagerly evicted when new activity events are recorded via
     * {@link de.tum.in.www1.hephaestus.activity.ActivityEventService}.
     *
     * <p>Cache metrics (hits, misses, evictions, size) are exposed via Micrometer
     * under the {@code cache.*} namespace with tag {@code cache=leaderboardXp}.
     *
     * @param workspaceId the workspace
     * @param since start of timeframe (inclusive)
     * @param until end of timeframe (exclusive)
     * @param teamIds team IDs to filter by (empty = all teams)
     * @return map of actor ID to XP data
     * @see de.tum.in.www1.hephaestus.config.CacheConfig
     */
    @Transactional(readOnly = true)
    @Cacheable(
        value = "leaderboardXp",
        key = "#workspaceId + '_' + #since.toEpochMilli() + '_' + #until.toEpochMilli() + '_' + #root.target.buildTeamIdsCacheKey(#teamIds)"
    )
    public Map<Long, LeaderboardUserXp> getLeaderboardData(
        Long workspaceId,
        Instant since,
        Instant until,
        Set<Long> teamIds
    ) {
        log.debug(
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
            log.debug("No activity events found for leaderboard");
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
                log.warn("User not found for actor ID: {}", actorId);
                continue;
            }

            // Use HALF_UP rounding for fair score calculation (not truncation)
            int totalScore = XpPrecision.roundToInt(xp.getTotalExperiencePoints());
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
                case REVIEW_UNKNOWN -> builder.addUnknowns(count);
                case COMMENT_CREATED -> builder.addIssueComments(count);
                case REVIEW_COMMENT_CREATED -> builder.addCodeComments(count);
                default -> {
                    // PR events and REVIEW_DISMISSED don't contribute to review stats
                }
            }
        }

        // 7. Query distinct PR counts per user from the reviews table
        // This counts DISTINCT PRs, not events (fixing numberOfReviewedPRs regression)
        Map<Long, Long> distinctPrCountsByUser = activityEventRepository.countDistinctReviewedPullRequestsByActors(
            workspaceId,
            actorIds,
            since,
            until
        );

        // Set the distinct PR counts on each builder
        for (Map.Entry<Long, LeaderboardUserXp.Builder> entry : builders.entrySet()) {
            Long actorId = entry.getKey();
            int prCount = distinctPrCountsByUser.getOrDefault(actorId, 0L).intValue();
            entry.getValue().withReviewedPrCount(prCount);
        }

        // 8. Build immutable results
        Map<Long, LeaderboardUserXp> result = builders
            .entrySet()
            .stream()
            .collect(toMap(Map.Entry::getKey, e -> e.getValue().build()));

        log.debug("Built leaderboard data for {} users", result.size());
        return result;
    }
}
