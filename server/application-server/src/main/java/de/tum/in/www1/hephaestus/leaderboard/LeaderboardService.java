package de.tum.in.www1.hephaestus.leaderboard;

import de.tum.in.www1.hephaestus.gitprovider.issuecomment.IssueComment;
import de.tum.in.www1.hephaestus.gitprovider.issuecomment.IssueCommentRepository;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequest;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequestInfoDTO;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreview.PullRequestReview;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreview.PullRequestReviewRepository;
import de.tum.in.www1.hephaestus.gitprovider.team.Team;
import de.tum.in.www1.hephaestus.gitprovider.team.TeamRepository;
import de.tum.in.www1.hephaestus.gitprovider.user.User;
import de.tum.in.www1.hephaestus.gitprovider.user.UserInfoDTO;
import de.tum.in.www1.hephaestus.gitprovider.user.UserRepository;
import jakarta.transaction.Transactional;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class LeaderboardService {

    private static final Logger logger = LoggerFactory.getLogger(LeaderboardService.class);

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PullRequestReviewRepository pullRequestReviewRepository;

    @Autowired
    private IssueCommentRepository issueCommentRepository;

    @Autowired
    private ScoringService scoringService;

    @Autowired
    private TeamRepository teamRepository;

    @Autowired
    private LeaguePointsCalculationService leaguePointsCalculationService;

    private Comparator<Map.Entry<Long, Integer>> getComparator(
        LeaderboardSortType sortType,
        Map<Long, User> usersById,
        Map<Long, List<PullRequestReview>> reviewsByUserId,
        Map<Long, List<IssueComment>> issueCommentsByUserId
    ) {
        return switch (sortType) {
            case LEAGUE_POINTS -> compareByLeaguePoints(usersById, reviewsByUserId, issueCommentsByUserId);
            case SCORE -> compareByScore(reviewsByUserId, issueCommentsByUserId);
        };
    }

    /**
     * Creates a comparator that compares users by their league points.
     * If two users have the same league points, they are compared by their score.
     * @param usersById map of users by ID
     * @param reviewsByUserId map of reviews by user ID
     * @param issueCommentsByUserId map of issue comments by user ID
     * @return a comparator that sorts by league points descending
     */
    private Comparator<Map.Entry<Long, Integer>> compareByLeaguePoints(
        Map<Long, User> usersById,
        Map<Long, List<PullRequestReview>> reviewsByUserId,
        Map<Long, List<IssueComment>> issueCommentsByUserId
    ) {
        return (e1, e2) -> {
            int e1LeaguePoints = usersById.get(e1.getKey()).getLeaguePoints();
            int e2LeaguePoints = usersById.get(e2.getKey()).getLeaguePoints();
            int leagueCompare = Integer.compare(e2LeaguePoints, e1LeaguePoints);
            if (leagueCompare != 0) {
                return leagueCompare;
            }
            return compareByScore(reviewsByUserId, issueCommentsByUserId).compare(e1, e2);
        };
    }

    /**
     * Creates a comparator that compares users by their score.
     * If two users have the same score, they are compared by the total number of comments.
     * @param reviewsByUserId map of reviews by user ID
     * @param issueCommentsByUserId map of issue comments by user ID
     * @return a comparator that sorts by score descending
     */
    private Comparator<Map.Entry<Long, Integer>> compareByScore(
        Map<Long, List<PullRequestReview>> reviewsByUserId,
        Map<Long, List<IssueComment>> issueCommentsByUserId
    ) {
        return (e1, e2) -> {
            int scoreCompare = e2.getValue().compareTo(e1.getValue());
            if (scoreCompare != 0) {
                return scoreCompare;
            }
            // If both users have a score of 0, compare by total comment count.
            // Calculate total code review comment count from reviews.
            int e1ReviewComments = reviewsByUserId
                .getOrDefault(e1.getKey(), List.of())
                .stream()
                .mapToInt(review -> review.getComments().size())
                .sum();
            int e2ReviewComments = reviewsByUserId
                .getOrDefault(e2.getKey(), List.of())
                .stream()
                .mapToInt(review -> review.getComments().size())
                .sum();
            // Calculate total issue comments.
            int e1IssueComments = issueCommentsByUserId.getOrDefault(e1.getKey(), List.of()).size();
            int e2IssueComments = issueCommentsByUserId.getOrDefault(e2.getKey(), List.of()).size();
            int e1TotalComments = e1ReviewComments + e1IssueComments;
            int e2TotalComments = e2ReviewComments + e2IssueComments;
            // Sort descending by total comment count.
            return Integer.compare(e2TotalComments, e1TotalComments);
        };
    }

    @Transactional
    public List<LeaderboardEntryDTO> createLeaderboard(
        OffsetDateTime after,
        OffsetDateTime before,
        Optional<String> team,
        Optional<LeaderboardSortType> sort
    ) {
        Optional<Team> teamEntity = team.map(t -> teamRepository.findByName(t)).orElse(Optional.empty());
        logger.info(
            "Creating leaderboard dataset with timeframe: {} - {} and team: {}",
            after,
            before,
            teamEntity.map(Team::getName).orElse("all")
        );

        List<PullRequestReview> reviews;
        List<IssueComment> issueComments;
        if (teamEntity.isPresent()) {
            reviews = pullRequestReviewRepository.findAllInTimeframeOfTeam(after, before, teamEntity.get().getId());
            issueComments = issueCommentRepository.findAllInTimeframeOfTeam(
                after,
                before,
                teamEntity.get().getId(),
                true
            );
        } else {
            reviews = pullRequestReviewRepository.findAllInTimeframe(after, before);
            issueComments = issueCommentRepository.findAllInTimeframe(after, before, true);
        }

        Map<Long, User> usersById = reviews
            .stream()
            .map(PullRequestReview::getAuthor)
            .collect(Collectors.toMap(User::getId, user -> user, (u1, u2) -> u1));

        issueComments.stream().map(IssueComment::getAuthor).forEach(user -> usersById.putIfAbsent(user.getId(), user));

        if (teamEntity.isPresent()) {
            userRepository
                .findAllByTeamId(teamEntity.get().getId())
                .forEach(user -> usersById.putIfAbsent(user.getId(), user));
        } else {
            // Show all active users in the total leaderboard
            userRepository.findAllHumanInTeams().stream().forEach(user -> usersById.putIfAbsent(user.getId(), user));
        }

        // Review activity
        Map<Long, List<PullRequestReview>> reviewsByUserId = reviews
            .stream()
            .collect(Collectors.groupingBy(review -> review.getAuthor().getId()));
        Map<Long, List<IssueComment>> issueCommentsByUserId = issueComments
            .stream()
            .collect(Collectors.groupingBy(comment -> comment.getAuthor().getId()));

        Map<Long, Integer> scoresByUserId = reviewsByUserId
            .entrySet()
            .stream()
            .collect(
                Collectors.toMap(Map.Entry::getKey, entry ->
                    calculateTotalScore(entry.getValue(), issueCommentsByUserId.getOrDefault(entry.getKey(), List.of()))
                )
            );

        // Add missing users with score 0
        usersById.keySet().forEach(userId -> scoresByUserId.putIfAbsent(userId, 0));

        // Ranking (sorted by score descending)
        List<Long> rankingByUserId = scoresByUserId
            .entrySet()
            .stream()
            .sorted(
                getComparator(sort.orElse(LeaderboardSortType.SCORE), usersById, reviewsByUserId, issueCommentsByUserId)
            )
            .map(Map.Entry::getKey)
            .collect(Collectors.toList());

        List<LeaderboardEntryDTO> leaderboard = IntStream.range(0, rankingByUserId.size())
            .mapToObj(index -> {
                int rank = index + 1;
                Long userId = rankingByUserId.get(index);
                int score = scoresByUserId.get(userId);
                UserInfoDTO user = UserInfoDTO.fromUser(usersById.get(userId));
                List<PullRequestReview> userReviews = reviewsByUserId.getOrDefault(userId, List.of());
                List<IssueComment> userIssueComments = issueCommentsByUserId.getOrDefault(userId, List.of());
                List<PullRequestInfoDTO> reviewedPullRequests = userReviews
                    .stream()
                    .map(review -> review.getPullRequest())
                    .filter(pullRequest -> pullRequest.getAuthor().getId() != userId)
                    // First collect to a map keyed by PR ID to ensure uniqueness
                    .collect(
                        Collectors.groupingBy(
                            PullRequest::getId, // Key by ID
                            Collectors.collectingAndThen(
                                Collectors.toList(),
                                list -> PullRequestInfoDTO.fromPullRequest(list.get(0)) // Convert first instance to DTO
                            )
                        )
                    )
                    .values()
                    .stream()
                    .collect(Collectors.toList());

                int numberOfReviewedPRs = userReviews
                    .stream()
                    .map(review -> review.getPullRequest().getId())
                    .collect(Collectors.toSet())
                    .size();
                int numberOfApprovals = (int) userReviews
                    .stream()
                    .filter(review -> review.getState() == PullRequestReview.State.APPROVED)
                    .count();
                int numberOfChangeRequests = (int) userReviews
                    .stream()
                    .filter(review -> review.getState() == PullRequestReview.State.CHANGES_REQUESTED)
                    .count();
                int numberOfComments = (int) userReviews
                    .stream()
                    .filter(review -> review.getState() == PullRequestReview.State.COMMENTED)
                    .filter(review -> review.getBody() != null)
                    .count();
                numberOfComments += userIssueComments.size();

                int numberOfUnknowns = (int) userReviews
                    .stream()
                    .filter(review -> review.getState() == PullRequestReview.State.UNKNOWN)
                    .filter(review -> review.getBody() != null)
                    .count();
                int numberOfCodeComments = userReviews
                    .stream()
                    .map(review -> review.getComments().size())
                    .reduce(0, Integer::sum);

                return new LeaderboardEntryDTO(
                    rank,
                    score,
                    user,
                    reviewedPullRequests,
                    numberOfReviewedPRs,
                    numberOfApprovals,
                    numberOfChangeRequests,
                    numberOfComments,
                    numberOfUnknowns,
                    numberOfCodeComments
                );
            })
            .toList();

        return leaderboard;
    }

    private int calculateTotalScore(List<PullRequestReview> reviews, List<IssueComment> issueComments) {
        int numberOfIssueComments = issueComments
            .stream()
            .filter(issueComment -> issueComment.getIssue().getAuthor().getId() != issueComment.getAuthor().getId())
            .collect(Collectors.toList())
            .size();
        // Could contain multiple reviews for the same pull request
        Map<Long, List<PullRequestReview>> reviewsByPullRequestId = reviews
            .stream()
            .collect(Collectors.groupingBy(review -> review.getPullRequest().getId()));

        double totalScore = reviewsByPullRequestId
            .values()
            .stream()
            .map(pullRequestReviews -> scoringService.calculateReviewScore(pullRequestReviews, numberOfIssueComments))
            .reduce(0.0, Double::sum);
        return (int) Math.ceil(totalScore);
    }

    /**
     * Get the league point change for a specific user
     * @param login user login
     * @param entry current leaderboard entry of the user
     * @return LeaderboardUserStatsDTO containing league point change
     */
    @Transactional
    public LeagueChangeDTO getUserLeagueStats(String login, LeaderboardEntryDTO entry) {
        // Get the user
        User user = userRepository
            .findByLogin(login)
            .orElseThrow(() -> new IllegalArgumentException("User not found with login: " + login));

        // Calculate league point change
        int currentLeaguePoints = user.getLeaguePoints();
        int projectedNewPoints = leaguePointsCalculationService.calculateNewPoints(user, entry);
        int leaguePointsChange = projectedNewPoints - currentLeaguePoints;

        return new LeagueChangeDTO(user.getLogin(), leaguePointsChange);
    }
}
