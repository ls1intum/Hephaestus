package de.tum.in.www1.hephaestus.leaderboard;

import static de.tum.in.www1.hephaestus.shared.LeaguePointsConstants.POINTS_DEFAULT;
import static java.util.function.Function.identity;

import de.tum.in.www1.hephaestus.core.exception.EntityNotFoundException;
import de.tum.in.www1.hephaestus.gitprovider.label.Label;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequest;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequestInfoDTO;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreview.PullRequestReview;
import de.tum.in.www1.hephaestus.gitprovider.team.Team;
import de.tum.in.www1.hephaestus.gitprovider.team.TeamInfoDTO;
import de.tum.in.www1.hephaestus.gitprovider.team.TeamRepository;
import de.tum.in.www1.hephaestus.gitprovider.user.User;
import de.tum.in.www1.hephaestus.gitprovider.user.UserInfoDTO;
import de.tum.in.www1.hephaestus.gitprovider.user.UserRepository;
import de.tum.in.www1.hephaestus.workspace.Workspace;
import de.tum.in.www1.hephaestus.workspace.WorkspaceMembershipService;
import de.tum.in.www1.hephaestus.workspace.settings.WorkspaceTeamSettingsService;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for building leaderboard data.
 *
 * <p>XP scores are read from the activity event ledger, NOT recalculated on-the-fly.
 * This ensures consistency with the persisted XP values.
 */
@Service
public class LeaderboardService {

    private static final Logger log = LoggerFactory.getLogger(LeaderboardService.class);

    private final UserRepository userRepository;
    private final LeaderboardReviewQueryRepository leaderboardReviewQueryRepository;
    private final LeaderboardXpQueryService leaderboardXpQueryService;
    private final TeamRepository teamRepository;
    private final TeamPathResolver teamPathResolver;
    private final LeaguePointsService leaguePointsService;
    private final WorkspaceMembershipService workspaceMembershipService;
    private final WorkspaceTeamSettingsService workspaceTeamSettingsService;

    public LeaderboardService(
        UserRepository userRepository,
        LeaderboardReviewQueryRepository leaderboardReviewQueryRepository,
        LeaderboardXpQueryService leaderboardXpQueryService,
        TeamRepository teamRepository,
        TeamPathResolver teamPathResolver,
        LeaguePointsService leaguePointsService,
        WorkspaceMembershipService workspaceMembershipService,
        WorkspaceTeamSettingsService workspaceTeamSettingsService
    ) {
        this.userRepository = userRepository;
        this.leaderboardReviewQueryRepository = leaderboardReviewQueryRepository;
        this.leaderboardXpQueryService = leaderboardXpQueryService;
        this.teamRepository = teamRepository;
        this.teamPathResolver = teamPathResolver;
        this.leaguePointsService = leaguePointsService;
        this.workspaceMembershipService = workspaceMembershipService;
        this.workspaceTeamSettingsService = workspaceTeamSettingsService;
    }

    @Transactional(readOnly = true)
    public List<LeaderboardEntryDTO> createLeaderboard(
        Workspace workspace,
        Instant after,
        Instant before,
        String team,
        LeaderboardSortType sort,
        LeaderboardMode mode
    ) {
        if (mode == LeaderboardMode.INDIVIDUAL) {
            Optional<Team> resolvedTeam = "all".equals(team)
                ? Optional.empty()
                : teamPathResolver.resolveByPath(workspace, team);
            return createIndividualLeaderboard(workspace, after, before, resolvedTeam, sort);
        }

        // Team mode aggregates across all visible teams; the team filter is ignored on purpose (documented in OpenAPI).
        return createTeamLeaderboard(workspace, after, before, sort);
    }

    private List<LeaderboardEntryDTO> createIndividualLeaderboard(
        Workspace workspace,
        Instant after,
        Instant before,
        Optional<Team> team,
        LeaderboardSortType sort
    ) {
        return createIndividualLeaderboard(workspace, after, before, team, sort, null);
    }

    private List<LeaderboardEntryDTO> createIndividualLeaderboard(
        Workspace workspace,
        Instant after,
        Instant before,
        Optional<Team> team,
        LeaderboardSortType sort,
        Map<Long, List<Team>> teamHierarchy
    ) {
        if (workspace == null || workspace.getId() == null) {
            log.warn("Skipping leaderboard dataset creation because workspace id is missing.");
            return Collections.emptyList();
        }

        log.info(
            "Creating leaderboard dataset with timeframe: {} - {} and team: {} for workspace {}",
            after,
            before,
            team.isEmpty() ? "all" : team.get().getName(),
            workspace.getWorkspaceSlug()
        );

        Long workspaceId = workspace.getId();

        // Collect team IDs for filtering (if team specified)
        Set<Long> teamIds = team
            .map(t ->
                teamPathResolver.collectDescendantIds(
                    t,
                    Objects.requireNonNullElseGet(teamHierarchy, () -> teamPathResolver.buildHierarchy(workspace))
                )
            )
            .orElse(Collections.emptySet());

        // ========================================================================
        // XP and breakdown from activity events (source of truth)
        // ========================================================================
        Map<Long, LeaderboardUserXp> activityData = new HashMap<>(
            leaderboardXpQueryService.getLeaderboardData(workspaceId, after, before, teamIds)
        );

        // ========================================================================
        // Include ALL team members, even those with zero activity
        // ========================================================================
        List<User> allTeamMembers;
        if (team.isPresent() && !teamIds.isEmpty()) {
            allTeamMembers = userRepository.findAllByTeamIds(teamIds);
        } else if (workspace.getAccountLogin() != null) {
            allTeamMembers = userRepository.findAllHumanInTeamsOfOrganization(workspace.getAccountLogin());
        } else {
            allTeamMembers = Collections.emptyList();
        }

        // Add zero-score entries for team members without activity
        for (User member : allTeamMembers) {
            if (member.getId() != null && !activityData.containsKey(member.getId())) {
                // New record signature: user, totalScore, eventCount, approvals, changeRequests,
                // comments, unknowns, issueComments, codeComments, reviewedPrCount
                activityData.put(member.getId(), new LeaderboardUserXp(member, 0, 0, 0, 0, 0, 0, 0, 0, 0));
            }
        }

        if (activityData.isEmpty()) {
            log.info("No team members found for leaderboard");
            return Collections.emptyList();
        }

        // ========================================================================
        // Fetch PR data for display (not for scoring)
        // ========================================================================
        Set<Long> actorIds = activityData.keySet();

        List<PullRequestReview> reviews;
        if (team.isPresent() && !teamIds.isEmpty()) {
            reviews = leaderboardReviewQueryRepository.findAllInTimeframeOfTeams(after, before, teamIds, workspaceId);
        } else {
            reviews = leaderboardReviewQueryRepository.findAllInTimeframe(after, before, workspaceId);
        }

        Map<Long, List<PullRequestReview>> reviewsByUserId = reviews
            .stream()
            .filter(r -> r.getAuthor() != null && r.getAuthor().getId() != null)
            .filter(r -> actorIds.contains(r.getAuthor().getId()))
            .collect(Collectors.groupingBy(r -> r.getAuthor().getId()));

        // ========================================================================
        // Get league points for ranking
        // ========================================================================
        Collection<User> users = activityData.values().stream().map(LeaderboardUserXp::user).toList();

        Map<Long, Integer> leaguePointsByUserId = workspaceMembershipService.getLeaguePointsSnapshot(
            users,
            workspaceId
        );

        // ========================================================================
        // Sort by specified criteria
        // ========================================================================
        Comparator<Map.Entry<Long, LeaderboardUserXp>> comparator = comparatorForActivityData(
            sort,
            leaguePointsByUserId
        );

        List<Long> ranking = activityData.entrySet().stream().sorted(comparator).map(Map.Entry::getKey).toList();

        // ========================================================================
        // Build DTOs
        // ========================================================================
        List<LeaderboardEntryDTO> result = new ArrayList<>();
        for (int index = 0; index < ranking.size(); index++) {
            Long userId = ranking.get(index);
            LeaderboardUserXp data = activityData.get(userId);
            if (data == null) {
                continue;
            }

            User user = data.user();
            int score = data.totalScore();

            UserInfoDTO userDto = UserInfoDTO.fromUser(user, leaguePointsByUserId.getOrDefault(userId, POINTS_DEFAULT));

            List<PullRequestReview> userReviews = reviewsByUserId.getOrDefault(userId, Collections.emptyList());

            // Extract reviewed PRs for display popover
            List<PullRequestInfoDTO> reviewedPullRequests = userReviews
                .stream()
                .map(PullRequestReview::getPullRequest)
                .filter(Objects::nonNull)
                .filter(pr -> pr.getAuthor() == null || !Objects.equals(pr.getAuthor().getId(), userId))
                .collect(Collectors.toMap(PullRequest::getId, pr -> pr, (a, b) -> a))
                .values()
                .stream()
                .map(PullRequestInfoDTO::fromPullRequest)
                .collect(Collectors.toList());

            // Stats come from activity events (pre-computed, not recalculated)
            LeaderboardEntryDTO entry = new LeaderboardEntryDTO(
                index + 1,
                score,
                userDto,
                null,
                reviewedPullRequests,
                data.reviewedPullRequestCount(),
                data.approvals(),
                data.changeRequests(),
                data.comments() + data.issueComments(),
                data.unknowns(), // numberOfUnknowns - now properly tracked in activity events
                data.codeComments()
            );
            result.add(entry);
        }

        return result;
    }

    /**
     * Comparator for activity-based leaderboard data.
     */
    private Comparator<Map.Entry<Long, LeaderboardUserXp>> comparatorForActivityData(
        LeaderboardSortType sort,
        Map<Long, Integer> leaguePointsByUserId
    ) {
        return (a, b) -> {
            LeaderboardUserXp dataA = a.getValue();
            LeaderboardUserXp dataB = b.getValue();

            return switch (sort) {
                case SCORE -> Integer.compare(dataB.totalScore(), dataA.totalScore());
                case LEAGUE_POINTS -> {
                    int lpA = leaguePointsByUserId.getOrDefault(a.getKey(), POINTS_DEFAULT);
                    int lpB = leaguePointsByUserId.getOrDefault(b.getKey(), POINTS_DEFAULT);
                    // Tie-breaker: use score if league points are equal
                    int cmp = Integer.compare(lpB, lpA);
                    if (cmp != 0) {
                        yield cmp;
                    }
                    yield Integer.compare(dataB.totalScore(), dataA.totalScore());
                }
            };
        };
    }

    private List<LeaderboardEntryDTO> createTeamLeaderboard(
        Workspace workspace,
        Instant after,
        Instant before,
        LeaderboardSortType sort
    ) {
        log.info(
            "Creating team leaderboard dataset with timeframe: {} - {} in workspace {}",
            after,
            before,
            workspace.getWorkspaceSlug()
        );

        Map<Long, List<Team>> teamHierarchy = teamPathResolver.buildHierarchy(workspace);
        // Use workspace-scoped hidden settings instead of deprecated Team.hidden field
        Set<Long> hiddenTeamIds = workspaceTeamSettingsService.getHiddenTeamIds(workspace.getId());
        List<Team> targetTeams = workspace.getAccountLogin() == null
            ? List.of()
            : teamRepository
                  .findAllByOrganizationIgnoreCase(workspace.getAccountLogin())
                  .stream()
                  .filter(team -> team.getId() != null && !hiddenTeamIds.contains(team.getId()))
                  .toList();

        if (targetTeams.isEmpty()) {
            log.info("No teams found for team leaderboard in workspace {}", workspace.getWorkspaceSlug());
            return Collections.emptyList();
        }

        Map<Team, TeamStats> teamStatsById = targetTeams
            .stream()
            .collect(
                Collectors.toMap(identity(), teamEntity -> {
                    List<LeaderboardEntryDTO> entries = createIndividualLeaderboard(
                        workspace,
                        after,
                        before,
                        Optional.of(teamEntity),
                        LeaderboardSortType.SCORE,
                        teamHierarchy
                    );
                    return aggregateTeamStats(entries);
                })
            );

        List<Map.Entry<Team, TeamStats>> sorted = teamStatsById
            .entrySet()
            .stream()
            .sorted((e1, e2) -> {
                int cmp;
                if (sort == LeaderboardSortType.SCORE) {
                    cmp = Integer.compare(e2.getValue().score(), e1.getValue().score());
                } else {
                    cmp = Integer.compare(e2.getValue().leaguePoints(), e1.getValue().leaguePoints());
                }
                if (cmp != 0) {
                    return cmp;
                }
                return e1.getKey().getName().compareTo(e2.getKey().getName());
            })
            .toList();

        List<LeaderboardEntryDTO> result = new ArrayList<>();
        Long workspaceId = workspace.getId();
        for (int i = 0; i < sorted.size(); i++) {
            Team teamEntity = sorted.get(i).getKey();
            TeamStats stats = sorted.get(i).getValue();
            int score = sort == LeaderboardSortType.SCORE ? stats.score() : stats.leaguePoints();

            // Get workspace-scoped settings for this team
            boolean isHiddenInWorkspace = hiddenTeamIds.contains(teamEntity.getId());
            Set<Label> workspaceLabels = workspaceTeamSettingsService.getTeamLabelFilters(
                workspaceId,
                teamEntity.getId()
            );
            Set<Long> hiddenRepoIds = workspaceTeamSettingsService.getHiddenRepositoryIdsByTeams(
                workspaceId,
                Set.of(teamEntity.getId())
            );

            LeaderboardEntryDTO entry = new LeaderboardEntryDTO(
                i + 1,
                score,
                null,
                TeamInfoDTO.fromTeamWithWorkspaceSettings(
                    teamEntity,
                    isHiddenInWorkspace,
                    workspaceLabels,
                    hiddenRepoIds
                ),
                stats.reviewedPullRequests(),
                stats.numberOfReviewedPRs(),
                stats.numberOfApprovals(),
                stats.numberOfChangeRequests(),
                stats.numberOfComments(),
                stats.numberOfUnknowns(),
                stats.numberOfCodeComments()
            );
            result.add(entry);
        }
        return result;
    }

    @Transactional(readOnly = true)
    public LeagueChangeDTO getUserLeagueStats(Workspace workspace, String login, LeaderboardEntryDTO entry) {
        User user = userRepository.findByLogin(login).orElseThrow(() -> new EntityNotFoundException("User", login));

        if (workspace == null || workspace.getId() == null) {
            throw new IllegalStateException("Workspace context is required to compute league stats");
        }

        Long workspaceId = workspace.getId();

        int currentLeaguePoints = workspaceMembershipService.getCurrentLeaguePoints(workspaceId, user);
        int projectedNewPoints = leaguePointsService.calculateNewPoints(user, currentLeaguePoints, entry);
        return new LeagueChangeDTO(user.getLogin(), projectedNewPoints - currentLeaguePoints);
    }

    private TeamStats aggregateTeamStats(List<LeaderboardEntryDTO> entries) {
        List<PullRequestInfoDTO> reviewedPullRequests = new ArrayList<>(
            entries
                .stream()
                .flatMap(e -> e.reviewedPullRequests().stream())
                .collect(Collectors.toMap(PullRequestInfoDTO::id, p -> p, (a, b) -> a))
                .values()
        );

        int score = entries.stream().mapToInt(LeaderboardEntryDTO::score).sum();
        int leaguePoints = entries
            .stream()
            .mapToInt(e -> e.user() == null ? 0 : e.user().leaguePoints())
            .sum();
        int numberOfReviewedPRs = entries.stream().mapToInt(LeaderboardEntryDTO::numberOfReviewedPRs).sum();
        int numberOfApprovals = entries.stream().mapToInt(LeaderboardEntryDTO::numberOfApprovals).sum();
        int numberOfChangeRequests = entries.stream().mapToInt(LeaderboardEntryDTO::numberOfChangeRequests).sum();
        int numberOfComments = entries.stream().mapToInt(LeaderboardEntryDTO::numberOfComments).sum();
        int numberOfUnknowns = entries.stream().mapToInt(LeaderboardEntryDTO::numberOfUnknowns).sum();
        int numberOfCodeComments = entries.stream().mapToInt(LeaderboardEntryDTO::numberOfCodeComments).sum();

        return new TeamStats(
            score,
            leaguePoints,
            reviewedPullRequests,
            numberOfReviewedPRs,
            numberOfApprovals,
            numberOfChangeRequests,
            numberOfComments,
            numberOfUnknowns,
            numberOfCodeComments
        );
    }

    private record TeamStats(
        int score,
        int leaguePoints,
        List<PullRequestInfoDTO> reviewedPullRequests,
        int numberOfReviewedPRs,
        int numberOfApprovals,
        int numberOfChangeRequests,
        int numberOfComments,
        int numberOfUnknowns,
        int numberOfCodeComments
    ) {}
}
