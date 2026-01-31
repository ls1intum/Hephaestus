package de.tum.in.www1.hephaestus.profile;

import de.tum.in.www1.hephaestus.activity.ActivityEvent;
import de.tum.in.www1.hephaestus.activity.ActivityEventRepository;
import de.tum.in.www1.hephaestus.activity.scoring.XpPrecision;
import de.tum.in.www1.hephaestus.core.LoggingUtils;
import de.tum.in.www1.hephaestus.gitprovider.issue.Issue;
import de.tum.in.www1.hephaestus.gitprovider.issuecomment.IssueComment;
import de.tum.in.www1.hephaestus.gitprovider.issuecomment.IssueCommentRepository;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequestInfoDTO;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequestRepository;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreview.PullRequestReview;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreview.PullRequestReviewRepository;
import de.tum.in.www1.hephaestus.gitprovider.repository.RepositoryInfoDTO;
import de.tum.in.www1.hephaestus.gitprovider.user.User;
import de.tum.in.www1.hephaestus.gitprovider.user.UserInfoDTO;
import de.tum.in.www1.hephaestus.gitprovider.user.UserRepository;
import de.tum.in.www1.hephaestus.profile.dto.ProfileActivityStatsDTO;
import de.tum.in.www1.hephaestus.profile.dto.ProfileDTO;
import de.tum.in.www1.hephaestus.profile.dto.ProfileReviewActivityDTO;
import de.tum.in.www1.hephaestus.workspace.WorkspaceContributionActivityService;
import de.tum.in.www1.hephaestus.workspace.WorkspaceMembershipService;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for user profile data aggregation.
 *
 * <p>Uses ActivityEvent as the single source of truth for review activity,
 * consistent with leaderboard queries. This ensures profile and leaderboard
 * display identical data with the same filtering rules:
 * <ul>
 *   <li>Workspace scoping via ActivityEvent.workspace.id (not RepositoryToMonitor)</li>
 *   <li>Hidden repo exclusion via WorkspaceTeamRepositorySettings</li>
 *   <li>Human users only (type = USER)</li>
 * </ul>
 *
 * <p>Architecture:
 * <pre>
 * ActivityEvent (source of truth)    profile (this)
 * ────────────────────────────────   ─────────────────────
 * activity_event                  →  ProfileActivityStatsDTO
 * (targets: review, comment)         ProfileReviewActivityDTO
 * </pre>
 *
 * <p><strong>Entity hydration:</strong> ActivityEvent contains target IDs and XP.
 * Entity details (PR title, author, etc.) are batch-fetched from gitprovider tables
 * using the target IDs.
 *
 * <p><strong>Time range convention:</strong> Uses half-open intervals [since, until)
 * consistent with leaderboard queries.
 */
@Service
public class UserProfileService {

    private static final Logger log = LoggerFactory.getLogger(UserProfileService.class);
    private static final Duration DEFAULT_ACTIVITY_WINDOW = Duration.ofDays(7);

    private final UserRepository userRepository;
    private final ProfileRepositoryQueryRepository profileRepositoryQueryRepository;
    private final ProfilePullRequestQueryRepository profilePullRequestQueryRepository;
    private final PullRequestReviewRepository pullRequestReviewRepository;
    private final IssueCommentRepository issueCommentRepository;
    private final ProfileReviewActivityAssembler reviewActivityAssembler;
    private final WorkspaceMembershipService workspaceMembershipService;
    private final WorkspaceContributionActivityService workspaceContributionActivityService;
    private final ProfileActivityQueryService profileActivityQueryService;
    private final PullRequestRepository pullRequestRepository;
    private final ActivityEventRepository activityEventRepository;

    public UserProfileService(
        UserRepository userRepository,
        ProfileRepositoryQueryRepository profileRepositoryQueryRepository,
        ProfilePullRequestQueryRepository profilePullRequestQueryRepository,
        PullRequestReviewRepository pullRequestReviewRepository,
        IssueCommentRepository issueCommentRepository,
        ProfileReviewActivityAssembler reviewActivityAssembler,
        WorkspaceMembershipService workspaceMembershipService,
        WorkspaceContributionActivityService workspaceContributionActivityService,
        ProfileActivityQueryService profileActivityQueryService,
        PullRequestRepository pullRequestRepository,
        ActivityEventRepository activityEventRepository
    ) {
        this.userRepository = userRepository;
        this.profileRepositoryQueryRepository = profileRepositoryQueryRepository;
        this.profilePullRequestQueryRepository = profilePullRequestQueryRepository;
        this.pullRequestReviewRepository = pullRequestReviewRepository;
        this.issueCommentRepository = issueCommentRepository;
        this.reviewActivityAssembler = reviewActivityAssembler;
        this.workspaceMembershipService = workspaceMembershipService;
        this.workspaceContributionActivityService = workspaceContributionActivityService;
        this.profileActivityQueryService = profileActivityQueryService;
        this.pullRequestRepository = pullRequestRepository;
        this.activityEventRepository = activityEventRepository;
    }

    /**
     * Get user profile with workspace-scoped activity data.
     *
     * @param login GitHub login
     * @param workspaceId workspace to scope activity to (null for global view)
     * @param after start of activity window (null for default 7 days before 'before')
     * @param before end of activity window (null for now)
     * @return user profile with open PRs, review activity with XP, etc.
     */
    @Transactional(readOnly = true)
    public Optional<ProfileDTO> getUserProfile(String login, Long workspaceId, Instant after, Instant before) {
        String safeLogin = LoggingUtils.sanitizeForLog(login);
        TimeRange timeRange = resolveTimeRange(login, after, before);
        String safeWorkspace = workspaceId == null ? "null" : LoggingUtils.sanitizeForLog(workspaceId.toString());
        String safeAfter = LoggingUtils.sanitizeForLog(timeRange.after().toString());
        String safeBefore = LoggingUtils.sanitizeForLog(timeRange.before().toString());
        log.debug(
            "Getting user profile for login: {} in workspace: {} with timeframe {} - {}",
            safeLogin,
            safeWorkspace,
            safeAfter,
            safeBefore
        );

        Optional<User> optionalUser = userRepository.findByLogin(login);
        if (optionalUser.isEmpty()) {
            return Optional.empty();
        }

        User userEntity = optionalUser.get();

        int leaguePoints = workspaceMembershipService.getCurrentLeaguePoints(workspaceId, userEntity);
        UserInfoDTO user = UserInfoDTO.fromUser(userEntity, leaguePoints);

        // First contribution is workspace-scoped to only show activity in monitored repositories
        var firstContribution =
            workspaceId == null
                ? null
                : workspaceContributionActivityService
                      .findFirstContributionInstant(workspaceId, userEntity.getId())
                      .orElse(null);

        List<PullRequestInfoDTO> openPullRequests =
            workspaceId == null
                ? List.of()
                : profilePullRequestQueryRepository
                      .findAssignedByLoginAndStates(login, Set.of(Issue.State.OPEN), workspaceId)
                      .stream()
                      .map(PullRequestInfoDTO::fromPullRequest)
                      .toList();

        List<RepositoryInfoDTO> contributedRepositories =
            workspaceId == null
                ? List.of()
                : profileRepositoryQueryRepository
                      .findContributedByLogin(login, workspaceId)
                      .stream()
                      .map(RepositoryInfoDTO::fromRepository)
                      .sorted(Comparator.comparing(RepositoryInfoDTO::name))
                      .toList();

        // Review activity: query PullRequestReview and IssueComment directly
        // This ensures all reviews are shown, regardless of ActivityEvent records
        List<ProfileReviewActivityDTO> reviewActivity = buildReviewActivity(
            userEntity.getLogin(),
            workspaceId,
            timeRange
        );

        // Activity stats from activity events (matches leaderboard semantics)
        ProfileActivityStatsDTO activityStats = profileActivityQueryService
            .getActivityStats(workspaceId, userEntity.getId(), timeRange.after(), timeRange.before())
            .map(stats ->
                new ProfileActivityStatsDTO.Builder()
                    .withScore(stats.totalScore())
                    .withNumberOfReviewedPRs(stats.reviewedPrCount())
                    .withNumberOfApprovals(stats.approvals())
                    .withNumberOfChangeRequests(stats.changeRequests())
                    .withNumberOfComments(stats.comments())
                    .withNumberOfIssueComments(stats.issueComments())
                    .withNumberOfCodeComments(stats.codeComments())
                    .withNumberOfUnknowns(stats.unknowns())
                    .build()
            )
            .orElse(ProfileActivityStatsDTO.empty());

        // Distinct PRs reviewed (hydrated from activity events)
        List<PullRequestInfoDTO> reviewedPullRequests = buildReviewedPullRequestsList(
            workspaceId,
            userEntity.getId(),
            timeRange
        );

        return Optional.of(
            new ProfileDTO(
                user,
                firstContribution,
                contributedRepositories,
                reviewActivity,
                openPullRequests,
                activityStats,
                reviewedPullRequests
            )
        );
    }

    private TimeRange resolveTimeRange(String login, Instant after, Instant before) {
        Instant resolvedBefore = before == null ? Instant.now() : before;
        Instant resolvedAfter = after == null ? resolvedBefore.minus(DEFAULT_ACTIVITY_WINDOW) : after;

        if (resolvedAfter.isAfter(resolvedBefore)) {
            log.debug(
                "Clamping activity window for user {} because after > before",
                LoggingUtils.sanitizeForLog(login)
            );
            resolvedAfter = resolvedBefore;
        }

        return new TimeRange(resolvedAfter, resolvedBefore);
    }

    /**
     * Build review activity by querying ActivityEvent table (same source as leaderboard).
     *
     * <p>This queries the ActivityEvent table first to get review/comment events,
     * then hydrates entity details. This ensures consistency with leaderboard counts
     * since both use ActivityEvent as the source of truth.
     *
     * <p>The ActivityEvent table uses direct workspace.id scoping (not RepositoryToMonitor),
     * and applies the same filters as leaderboard queries:
     * <ul>
     *   <li>Hidden repo exclusion via WorkspaceTeamRepositorySettings</li>
     *   <li>Human users only (type = USER)</li>
     * </ul>
     *
     * <p><strong>Time range convention:</strong> Uses half-open interval [since, until)
     * consistent with leaderboard queries.
     */
    private List<ProfileReviewActivityDTO> buildReviewActivity(String login, Long workspaceId, TimeRange timeRange) {
        if (workspaceId == null || login == null) {
            return List.of();
        }

        // Get user ID from login
        Optional<User> userOpt = userRepository.findByLogin(login);
        if (userOpt.isEmpty()) {
            return List.of();
        }
        Long userId = userOpt.get().getId();

        // Query ActivityEvent table (same source as leaderboard)
        List<ActivityEvent> activityEvents = activityEventRepository.findProfileActivityByActorInTimeframe(
            workspaceId,
            userId,
            timeRange.after(),
            timeRange.before()
        );

        if (activityEvents.isEmpty()) {
            return List.of();
        }

        // Separate review events from comment events
        Set<Long> reviewIds = activityEvents
            .stream()
            .filter(e -> "review".equals(e.getTargetType()))
            .map(ActivityEvent::getTargetId)
            .collect(Collectors.toSet());

        Set<Long> commentIds = activityEvents
            .stream()
            .filter(e -> "issue_comment".equals(e.getTargetType()))
            .map(ActivityEvent::getTargetId)
            .collect(Collectors.toSet());

        // Batch-fetch entity details
        Map<Long, PullRequestReview> reviewsById = reviewIds.isEmpty()
            ? Map.of()
            : pullRequestReviewRepository
                  .findAllByIdWithRelations(reviewIds)
                  .stream()
                  .collect(Collectors.toMap(PullRequestReview::getId, Function.identity()));

        Map<Long, IssueComment> commentsById = commentIds.isEmpty()
            ? Map.of()
            : issueCommentRepository
                  .findAllByIdWithRelations(commentIds)
                  .stream()
                  .collect(Collectors.toMap(IssueComment::getId, Function.identity()));

        // Build XP lookup map from activity events
        Map<Long, Double> xpByTargetId = activityEvents
            .stream()
            .collect(
                Collectors.toMap(
                    ActivityEvent::getTargetId,
                    ActivityEvent::getXp,
                    (xp1, xp2) -> xp1 + xp2 // Sum if same targetId appears multiple times
                )
            );

        // Assemble DTOs from activity events
        List<ProfileReviewActivityDTO> reviewActivity = activityEvents
            .stream()
            .map(event -> {
                int xp = XpPrecision.roundToInt(xpByTargetId.getOrDefault(event.getTargetId(), 0.0));
                if ("review".equals(event.getTargetType())) {
                    PullRequestReview review = reviewsById.get(event.getTargetId());
                    if (review != null) {
                        return reviewActivityAssembler.assemble(review, xp);
                    }
                } else if ("issue_comment".equals(event.getTargetType())) {
                    IssueComment comment = commentsById.get(event.getTargetId());
                    if (comment != null) {
                        return reviewActivityAssembler.assemble(comment, xp);
                    }
                }
                return null;
            })
            .filter(dto -> dto != null)
            // Deduplicate by ID (same review can have multiple events like EDITED)
            .collect(
                Collectors.toMap(
                    ProfileReviewActivityDTO::id,
                    Function.identity(),
                    (existing, replacement) -> existing // Keep first occurrence
                )
            )
            .values()
            .stream()
            .sorted(Comparator.comparing(ProfileReviewActivityDTO::submittedAt).reversed())
            .toList();

        return reviewActivity;
    }

    /**
     * Build list of distinct pull requests reviewed by the user.
     *
     * <p>Queries activity events for distinct PR IDs, then hydrates them using
     * the PullRequestRepository. This matches the leaderboard's approach of
     * using activity events as the source of truth for reviewed PRs.
     *
     * @param workspaceId workspace to scope to
     * @param userId the user's ID
     * @param timeRange the time range for activity
     * @return list of distinct PRs reviewed, or empty list if no data
     */
    private List<PullRequestInfoDTO> buildReviewedPullRequestsList(Long workspaceId, Long userId, TimeRange timeRange) {
        if (workspaceId == null || userId == null) {
            return List.of();
        }

        List<Long> prIds = activityEventRepository.findDistinctReviewedPullRequestIdsByActor(
            workspaceId,
            userId,
            timeRange.after(),
            timeRange.before()
        );

        if (prIds.isEmpty()) {
            return List.of();
        }

        return pullRequestRepository.findAllById(prIds).stream().map(PullRequestInfoDTO::fromPullRequest).toList();
    }

    private record TimeRange(Instant after, Instant before) {}
}
