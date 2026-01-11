package de.tum.in.www1.hephaestus.profile;

import de.tum.in.www1.hephaestus.activity.ActivityEventRepository;
import de.tum.in.www1.hephaestus.activity.ActivityTargetType;
import de.tum.in.www1.hephaestus.activity.scoring.XpPrecision;
import de.tum.in.www1.hephaestus.core.LoggingUtils;
import de.tum.in.www1.hephaestus.gitprovider.issue.Issue;
import de.tum.in.www1.hephaestus.gitprovider.issuecomment.IssueComment;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequestInfoDTO;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreview.PullRequestReview;
import de.tum.in.www1.hephaestus.gitprovider.repository.RepositoryInfoDTO;
import de.tum.in.www1.hephaestus.gitprovider.user.User;
import de.tum.in.www1.hephaestus.gitprovider.user.UserInfoDTO;
import de.tum.in.www1.hephaestus.gitprovider.user.UserRepository;
import de.tum.in.www1.hephaestus.profile.dto.ProfileDTO;
import de.tum.in.www1.hephaestus.profile.dto.ProfileReviewActivityDTO;
import de.tum.in.www1.hephaestus.workspace.WorkspaceContributionActivityService;
import de.tum.in.www1.hephaestus.workspace.WorkspaceMembershipService;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for user profile data aggregation.
 *
 * <p>Combines git provider data (PRs, reviews) with activity XP and workspace
 * membership data (league points). XP values are read from the activity_event
 * ledger (CQRS pattern) rather than recalculated.
 *
 * <p>Architecture:
 * <pre>
 * gitprovider (ETL)          activity (XP source)        profile (this)
 * ────────────────           ──────────────────          ─────────────
 * PullRequestReview    +     activity_event.xp     →     ProfileReviewActivityDTO
 * IssueComment               (pre-computed)              ProfileDTO
 * </pre>
 */
@Service
public class UserProfileService {

    private static final Logger log = LoggerFactory.getLogger(UserProfileService.class);
    private static final Duration DEFAULT_ACTIVITY_WINDOW = Duration.ofDays(7);

    private final UserRepository userRepository;
    private final ProfileRepositoryQueryRepository profileRepositoryQueryRepository;
    private final ProfilePullRequestQueryRepository profilePullRequestQueryRepository;
    private final ProfileReviewQueryRepository profileReviewQueryRepository;
    private final ProfileCommentQueryRepository profileCommentQueryRepository;
    private final ActivityEventRepository activityEventRepository;
    private final ProfileReviewActivityAssembler reviewActivityAssembler;
    private final WorkspaceMembershipService workspaceMembershipService;
    private final WorkspaceContributionActivityService workspaceContributionActivityService;

    public UserProfileService(
        UserRepository userRepository,
        ProfileRepositoryQueryRepository profileRepositoryQueryRepository,
        ProfilePullRequestQueryRepository profilePullRequestQueryRepository,
        ProfileReviewQueryRepository profileReviewQueryRepository,
        ProfileCommentQueryRepository profileCommentQueryRepository,
        ActivityEventRepository activityEventRepository,
        ProfileReviewActivityAssembler reviewActivityAssembler,
        WorkspaceMembershipService workspaceMembershipService,
        WorkspaceContributionActivityService workspaceContributionActivityService
    ) {
        this.userRepository = userRepository;
        this.profileRepositoryQueryRepository = profileRepositoryQueryRepository;
        this.profilePullRequestQueryRepository = profilePullRequestQueryRepository;
        this.profileReviewQueryRepository = profileReviewQueryRepository;
        this.profileCommentQueryRepository = profileCommentQueryRepository;
        this.activityEventRepository = activityEventRepository;
        this.reviewActivityAssembler = reviewActivityAssembler;
        this.workspaceMembershipService = workspaceMembershipService;
        this.workspaceContributionActivityService = workspaceContributionActivityService;
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
        var firstContribution = workspaceId == null
            ? null
            : workspaceContributionActivityService.findFirstContributionInstant(workspaceId, userEntity.getId()).orElse(null);

        List<PullRequestInfoDTO> openPullRequests = workspaceId == null
            ? List.of()
            : profilePullRequestQueryRepository
                  .findAssignedByLoginAndStates(login, Set.of(Issue.State.OPEN), workspaceId)
                  .stream()
                  .map(PullRequestInfoDTO::fromPullRequest)
                  .toList();

        List<RepositoryInfoDTO> contributedRepositories = workspaceId == null
            ? List.of()
            : profileRepositoryQueryRepository
                  .findContributedByLogin(login, workspaceId)
                  .stream()
                  .map(RepositoryInfoDTO::fromRepository)
                  .sorted(Comparator.comparing(RepositoryInfoDTO::name))
                  .toList();

        // Review activity: compose git provider data with XP from activity ledger
        List<ProfileReviewActivityDTO> reviewActivity = buildReviewActivity(login, workspaceId, timeRange);

        return Optional.of(
            new ProfileDTO(user, firstContribution, contributedRepositories, reviewActivity, openPullRequests)
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
     * Build review activity by composing git provider entities with XP from activity ledger.
     */
    private List<ProfileReviewActivityDTO> buildReviewActivity(String login, Long workspaceId, TimeRange timeRange) {
        if (workspaceId == null) {
            return List.of();
        }

        // 1. Fetch reviews and comments from git provider (pure ETL data)
        List<PullRequestReview> reviews = profileReviewQueryRepository.findAllByAuthorLoginInTimeframe(
            login,
            timeRange.after(),
            timeRange.before(),
            workspaceId
        );

        List<IssueComment> comments = profileCommentQueryRepository.findAllByAuthorLoginInTimeframe(
            login,
            timeRange.after(),
            timeRange.before(),
            true,
            workspaceId
        );

        if (reviews.isEmpty() && comments.isEmpty()) {
            return List.of();
        }

        // 2. Batch-fetch XP from activity_event ledger (CQRS: single source of truth)
        Set<Long> allTargetIds = Stream.concat(
            reviews.stream().map(PullRequestReview::getId),
            comments.stream().map(IssueComment::getId)
        ).collect(Collectors.toSet());

        Map<Long, Integer> xpByTargetId = activityEventRepository
            .findXpByTargetIdsAndTypes(
                workspaceId,
                allTargetIds,
                Set.of(ActivityTargetType.REVIEW, ActivityTargetType.ISSUE_COMMENT)
            )
            .stream()
            .collect(
                Collectors.toMap(
                    ActivityEventRepository.TargetXpProjection::getTargetId,
                    p -> XpPrecision.roundToInt(p.getXp()), // HALF_UP rounding for fairness
                    (a, b) -> a // In case of duplicates, keep first
                )
            );

        // 3. Assemble profile DTOs by composing git data + XP
        List<ProfileReviewActivityDTO> reviewActivity = new ArrayList<>();

        for (PullRequestReview review : reviews) {
            int xp = xpByTargetId.getOrDefault(review.getId(), 0);
            reviewActivity.add(reviewActivityAssembler.assemble(review, xp));
        }

        for (IssueComment comment : comments) {
            int xp = xpByTargetId.getOrDefault(comment.getId(), 0);
            reviewActivity.add(reviewActivityAssembler.assemble(comment, xp));
        }

        reviewActivity.sort(Comparator.comparing(ProfileReviewActivityDTO::submittedAt).reversed());
        return reviewActivity;
    }

    private record TimeRange(Instant after, Instant before) {}
}
