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

    @Transactional
    public List<LeaderboardEntryDTO> createLeaderboard(
        OffsetDateTime after,
        OffsetDateTime before,
        Optional<String> team
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
            .sorted(Map.Entry.comparingByValue(Comparator.reverseOrder()))
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
}
