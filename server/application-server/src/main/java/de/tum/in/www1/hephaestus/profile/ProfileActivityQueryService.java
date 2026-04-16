package de.tum.in.www1.hephaestus.profile;

import de.tum.in.www1.hephaestus.activity.ActivityBreakdownProjection;
import de.tum.in.www1.hephaestus.activity.ActivityEventRepository;
import de.tum.in.www1.hephaestus.activity.ActivityXpProjection;
import de.tum.in.www1.hephaestus.activity.scoring.XpPrecision;
import de.tum.in.www1.hephaestus.profile.ProfilePullRequestQueryRepository.AuthorCountProjection;
import de.tum.in.www1.hephaestus.profile.dto.ProfileActivityStatsDTO;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Queries profile activity statistics from the activity event ledger.
 *
 * <p>This service mirrors {@link de.tum.in.www1.hephaestus.leaderboard.LeaderboardXpQueryService}
 * but for a single user. It uses the same query patterns and semantics to ensure
 * consistency between profile and leaderboard views.
 *
 * <p>Architecture:
 * <pre>
 * ActivityEvent table (activity module)
 *         ↓
 * ProfileActivityQueryService (this) ← UserProfileService
 * </pre>
 *
 * <p><strong>Time range convention:</strong> All timeframe queries use half-open intervals
 * [since, until) - inclusive start, exclusive end. This ensures consistency with
 * the leaderboard module and prevents double-counting at interval boundaries.
 */
@Service
public class ProfileActivityQueryService {

    private static final Logger log = LoggerFactory.getLogger(ProfileActivityQueryService.class);

    private final ActivityEventRepository activityEventRepository;
    private final ProfilePullRequestQueryRepository profilePullRequestQueryRepository;

    public ProfileActivityQueryService(
        ActivityEventRepository activityEventRepository,
        ProfilePullRequestQueryRepository profilePullRequestQueryRepository
    ) {
        this.activityEventRepository = activityEventRepository;
        this.profilePullRequestQueryRepository = profilePullRequestQueryRepository;
    }

    /**
     * Get activity statistics for a single actor in a workspace timeframe.
     *
     * <p>Uses the same queries as the leaderboard:
     * <ul>
     *   <li>{@code findExperiencePointsByWorkspaceAndTimeframe} for XP totals</li>
     *   <li>{@code findActivityBreakdown} for counts by event type</li>
     *   <li>{@code countDistinctReviewedPullRequestsByActors} for distinct PR count</li>
     * </ul>
     *
     * @param workspaceId the workspace to scope activity to
     * @param actorId the actor (user) to get stats for
     * @param since start of timeframe (inclusive)
     * @param until end of timeframe (exclusive)
     * @return activity stats for the actor
     */
    @Transactional(readOnly = true)
    public Optional<ProfileActivityStatsDTO> getActivityStats(
        Long workspaceId,
        Long actorId,
        Instant since,
        Instant until
    ) {
        log.debug(
            "Fetching profile activity stats: workspaceId={}, actorId={}, since={}, until={}",
            workspaceId,
            actorId,
            since,
            until
        );

        Set<Long> actorIds = Set.of(actorId);

        // 1. Get XP totals from activity_event table
        List<ActivityXpProjection> xpData = activityEventRepository.findExperiencePointsByWorkspaceAndTimeframe(
            workspaceId,
            since,
            until
        );

        // Filter to the specific actor
        Optional<ActivityXpProjection> actorXp = xpData
            .stream()
            .filter(xp -> actorId.equals(xp.getActorId()))
            .findFirst();

        int totalScore = actorXp
            .map(ActivityXpProjection::getTotalExperiencePoints)
            .map(XpPrecision::roundToInt)
            .orElse(0);

        // 2. Get activity breakdown by type
        List<ActivityBreakdownProjection> breakdown = activityEventRepository.findActivityBreakdown(
            workspaceId,
            actorIds,
            since,
            until
        );

        Map<Long, Long> ownReplies = activityEventRepository.countOwnPullRequestRepliesByActors(
            workspaceId,
            actorIds,
            since,
            until
        );
        Map<Long, Long> openPullRequests = profilePullRequestQueryRepository
            .countOpenPullRequestsByAuthors(workspaceId, actorIds, since, until)
            .stream()
            .collect(
                java.util.stream.Collectors.toMap(AuthorCountProjection::getAuthorId, AuthorCountProjection::getCount)
            );
        Map<Long, Long> mergedPullRequests = profilePullRequestQueryRepository
            .countMergedPullRequestsByAuthors(workspaceId, actorIds, since, until)
            .stream()
            .collect(
                java.util.stream.Collectors.toMap(AuthorCountProjection::getAuthorId, AuthorCountProjection::getCount)
            );
        Map<Long, Long> closedPullRequests = profilePullRequestQueryRepository
            .countClosedPullRequestsByAuthors(workspaceId, actorIds, since, until)
            .stream()
            .collect(
                java.util.stream.Collectors.toMap(AuthorCountProjection::getAuthorId, AuthorCountProjection::getCount)
            );

        // 3. Aggregate breakdown stats
        int approvals = 0;
        int changeRequests = 0;
        int comments = 0;
        int unknowns = 0;
        int codeComments = 0;
        int openedIssues = 0;
        int closedIssues = 0;

        for (ActivityBreakdownProjection stat : breakdown) {
            int count = stat.getCount() != null ? stat.getCount().intValue() : 0;

            switch (stat.getEventType()) {
                case REVIEW_APPROVED -> approvals += count;
                case REVIEW_CHANGES_REQUESTED -> changeRequests += count;
                case REVIEW_COMMENTED -> comments += count;
                case REVIEW_UNKNOWN -> unknowns += count;
                case REVIEW_COMMENT_CREATED -> codeComments += count;
                case ISSUE_CREATED -> openedIssues += count;
                case ISSUE_CLOSED -> closedIssues += count;
                default -> {
                    // PR events and REVIEW_DISMISSED don't contribute to review stats
                }
            }
        }

        // 4. Query distinct PR count (with self-review exclusion)
        Map<Long, Long> distinctPrCounts = activityEventRepository.countDistinctReviewedPullRequestsByActors(
            workspaceId,
            actorIds,
            since,
            until
        );
        int reviewedPrCount = distinctPrCounts.getOrDefault(actorId, 0L).intValue();

        log.debug(
            "Built profile activity stats: actorId={}, totalScore={}, reviewedPrCount={}",
            actorId,
            totalScore,
            reviewedPrCount
        );

        return Optional.of(
            new ProfileActivityStatsDTO(
                totalScore,
                reviewedPrCount,
                approvals,
                changeRequests,
                comments,
                codeComments,
                unknowns,
                ownReplies.getOrDefault(actorId, 0L).intValue(),
                openPullRequests.getOrDefault(actorId, 0L).intValue(),
                mergedPullRequests.getOrDefault(actorId, 0L).intValue(),
                closedPullRequests.getOrDefault(actorId, 0L).intValue(),
                openedIssues,
                closedIssues
            )
        );
    }
}
