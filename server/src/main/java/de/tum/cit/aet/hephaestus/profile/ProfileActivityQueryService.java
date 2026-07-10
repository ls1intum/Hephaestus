package de.tum.cit.aet.hephaestus.profile;

import de.tum.cit.aet.hephaestus.activity.ActivityBreakdownProjection;
import de.tum.cit.aet.hephaestus.activity.ActivityEventRepository;
import de.tum.cit.aet.hephaestus.profile.ProfilePullRequestQueryRepository.AuthorCountProjection;
import de.tum.cit.aet.hephaestus.profile.dto.ProfileActivityStatsDTO;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Profile-scoped counterpart to the activity ledger, using the same activity ledger. */
@Service
@RequiredArgsConstructor
public class ProfileActivityQueryService {

    private static final Logger log = LoggerFactory.getLogger(ProfileActivityQueryService.class);

    private final ActivityEventRepository activityEventRepository;
    private final ProfilePullRequestQueryRepository profilePullRequestQueryRepository;

    @Transactional(readOnly = true)
    public ProfileActivityStatsDTO getActivityStats(Long workspaceId, Long actorId, Instant since, Instant until) {
        log.debug(
            "Fetching profile activity stats: workspaceId={}, actorId={}, since={}, until={}",
            workspaceId,
            actorId,
            since,
            until
        );

        Set<Long> actorIds = Set.of(actorId);

        // Get activity breakdown by type
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
        Map<Long, Long> openPullRequests = toAuthorCountMap(
            profilePullRequestQueryRepository.countOpenPullRequestsByAuthors(workspaceId, actorIds, since, until)
        );
        Map<Long, Long> mergedPullRequests = toAuthorCountMap(
            profilePullRequestQueryRepository.countMergedPullRequestsByAuthors(workspaceId, actorIds, since, until)
        );
        Map<Long, Long> closedPullRequests = toAuthorCountMap(
            profilePullRequestQueryRepository.countClosedPullRequestsByAuthors(workspaceId, actorIds, since, until)
        );

        // Aggregate breakdown stats
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

        // Query distinct PR count (with self-review exclusion)
        Map<Long, Long> distinctPrCounts = activityEventRepository.countDistinctReviewedPullRequestsByActors(
            workspaceId,
            actorIds,
            since,
            until
        );
        int reviewedPrCount = distinctPrCounts.getOrDefault(actorId, 0L).intValue();

        log.debug("Built profile activity stats: actorId={}, reviewedPrCount={}", actorId, reviewedPrCount);

        return new ProfileActivityStatsDTO(
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
        );
    }

    private static Map<Long, Long> toAuthorCountMap(List<AuthorCountProjection> counts) {
        return counts
            .stream()
            .collect(Collectors.toMap(AuthorCountProjection::getAuthorId, AuthorCountProjection::getCount));
    }
}
