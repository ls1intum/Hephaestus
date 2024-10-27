package de.tum.in.www1.hephaestus.leaderboard;

import java.util.stream.IntStream;
import java.util.Map;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import de.tum.in.www1.hephaestus.gitprovider.issuecomment.IssueComment;
import de.tum.in.www1.hephaestus.gitprovider.issuecomment.IssueCommentRepository;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequest;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreview.PullRequestReview;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreview.PullRequestReviewRepository;
import de.tum.in.www1.hephaestus.gitprovider.team.Team;
import de.tum.in.www1.hephaestus.gitprovider.team.TeamRepository;
import de.tum.in.www1.hephaestus.gitprovider.user.User;
import de.tum.in.www1.hephaestus.gitprovider.user.UserInfoDTO;
import jakarta.transaction.Transactional;

@Service
public class LeaderboardService {
        private static final Logger logger = LoggerFactory.getLogger(LeaderboardService.class);

        @Autowired
        private PullRequestReviewRepository pullRequestReviewRepository;
        @Autowired
        private IssueCommentRepository issueCommentRepository;
        @Autowired
        private TeamRepository teamRepository;

        @Value("${monitoring.timeframe}")
        private int timeframe;

        @Transactional
        public List<LeaderboardEntryDTO> createLeaderboard(Optional<LocalDate> after, Optional<LocalDate> before,
                        Optional<String> team) {
                logger.info("Creating leaderboard dataset");

                LocalDateTime afterCutOff = after.isPresent() ? after.get().atStartOfDay()
                                : LocalDate.now().minusDays(timeframe).atStartOfDay();
                Optional<LocalDateTime> beforeCutOff = before.map(date -> date.plusDays(1).atStartOfDay());
                Optional<Team> teamEntity = team.map((t) -> teamRepository.findByName(t)).orElse(Optional.empty());

                var afterOffset = afterCutOff.atOffset(ZoneOffset.UTC);
                var beforeOffset = beforeCutOff.map(b -> b.atOffset(ZoneOffset.UTC)).orElse(OffsetDateTime.now());
                List<PullRequestReview> reviews = pullRequestReviewRepository.findAllInTimeframe(afterOffset,
                                beforeOffset,
                                teamEntity);
                List<IssueComment> issueComments = issueCommentRepository.findAllInTimeframe(afterOffset, beforeOffset,
                                teamEntity, true);

                Map<Long, User> usersById = reviews.stream().map(PullRequestReview::getAuthor)
                                .collect(Collectors.toMap(User::getId, user -> user, (u1, u2) -> u1));

                // Review activity
                Map<Long, List<PullRequestReview>> reviewsByUserId = reviews.stream()
                                .collect(Collectors.groupingBy(review -> review.getAuthor().getId()));
                Map<Long, List<IssueComment>> issueCommentsByUserId = issueComments.stream()
                                .collect(Collectors.groupingBy(comment -> comment.getAuthor().getId()));

                Map<Long, Integer> scoresByUserId = reviewsByUserId.entrySet().stream()
                                .collect(Collectors.toMap(Map.Entry::getKey, entry -> calculateTotalScore(
                                                entry.getValue(),
                                                issueCommentsByUserId.getOrDefault(entry.getKey(), List.of()).size())));

                // Ranking (sorted by score descending)
                List<Long> rankingByUserId = scoresByUserId.entrySet().stream()
                                .sorted(Map.Entry.comparingByValue(Comparator.reverseOrder()))
                                .map(Map.Entry::getKey)
                                .collect(Collectors.toList());

                List<LeaderboardEntryDTO> leaderboard = IntStream.range(0, rankingByUserId.size())
                                .mapToObj(index -> {
                                        int rank = index + 1;
                                        Long userId = rankingByUserId.get(index);
                                        int score = scoresByUserId.get(userId);
                                        UserInfoDTO user = UserInfoDTO.fromUser(usersById.get(userId));
                                        List<PullRequestReview> userReviews = reviewsByUserId.getOrDefault(userId,
                                                        List.of());
                                        List<IssueComment> userIssueComments = issueCommentsByUserId
                                                        .getOrDefault(userId, List.of());

                                        int numberOfReviewedPRs = userReviews.stream()
                                                        .map(review -> review.getPullRequest().getId())
                                                        .collect(Collectors.toSet()).size();
                                        int numberOfApprovals = (int) userReviews.stream()
                                                        .filter(review -> review
                                                                        .getState() == PullRequestReview.State.APPROVED)
                                                        .count();
                                        int numberOfChangeRequests = (int) userReviews.stream()
                                                        .filter(review -> review
                                                                        .getState() == PullRequestReview.State.CHANGES_REQUESTED)
                                                        .count();
                                        int numberOfComments = (int) userReviews.stream()
                                                        .filter(review -> review
                                                                        .getState() == PullRequestReview.State.COMMENTED)
                                                        .filter(review -> review.getBody() != null).count();
                                        numberOfComments += userIssueComments.size();

                                        int numberOfUnknowns = (int) userReviews.stream()
                                                        .filter(review -> review
                                                                        .getState() == PullRequestReview.State.UNKNOWN)
                                                        .filter(review -> review.getBody() != null).count();
                                        int numberOfCodeComments = userReviews.stream()
                                                        .map(review -> review.getComments().size()).reduce(0,
                                                                        Integer::sum);

                                        return new LeaderboardEntryDTO(rank, score, user, numberOfReviewedPRs,
                                                        numberOfApprovals,
                                                        numberOfChangeRequests, numberOfComments, numberOfUnknowns,
                                                        numberOfCodeComments);
                                })
                                .toList();

                return leaderboard;
        }

        private int calculateTotalScore(List<PullRequestReview> reviews, int numberOfIssueComments) {
                // Could contain multiple reviews for the same pull request
                Map<Long, List<PullRequestReview>> reviewsByPullRequestId = reviews.stream()
                                .collect(Collectors.groupingBy(review -> review.getPullRequest().getId()));

                double totalScore = reviewsByPullRequestId
                                .values()
                                .stream()
                                .map(pullRequestReviews -> {
                                        // All reviews are for the same pull request
                                        int complexityScore = calculateComplexityScore(
                                                        pullRequestReviews.get(0).getPullRequest());

                                        int approvalReviews = (int) pullRequestReviews.stream()
                                                        .filter(review -> review
                                                                        .getState() == PullRequestReview.State.APPROVED)
                                                        .count();
                                        int changesRequestedReviews = (int) pullRequestReviews.stream()
                                                        .filter(review -> review
                                                                        .getState() == PullRequestReview.State.CHANGES_REQUESTED)
                                                        .count();
                                        int commentReviews = (int) pullRequestReviews.stream()
                                                        .filter(review -> review
                                                                        .getState() == PullRequestReview.State.COMMENTED)
                                                        .filter(review -> review.getBody() != null) // Only count if
                                                                                                    // there is a
                                                                                                    // comment
                                                        .count();
                                        int unknownReviews = (int) pullRequestReviews.stream()
                                                        .filter(review -> review
                                                                        .getState() == PullRequestReview.State.UNKNOWN)
                                                        .filter(review -> review.getBody() != null) // Only count if
                                                                                                    // there is a
                                                                                                    // comment
                                                        .count();

                                        int codeComments = pullRequestReviews.stream()
                                                        .map(review -> review.getComments().size())
                                                        .reduce(0, Integer::sum);

                                        double interactionScore = (approvalReviews * 1.5 +
                                                        changesRequestedReviews * 2.0 +
                                                        (commentReviews + unknownReviews + numberOfIssueComments) +
                                                        codeComments * 0.5);

                                        double complexityBonus = 1 + (complexityScore - 1) / 32.0;

                                        return 5 * interactionScore * complexityBonus;
                                })
                                .reduce(0.0, Double::sum);
                return (int) Math.ceil(totalScore);
        }

        /**
         * Calculates the complexity score for a given pull request.
         * Possible values: 1, 3, 7, 17, 33.
         * Taken from the original leaderboard implementation script.
         * 
         * @param pullRequest
         * @return score
         */
        private int calculateComplexityScore(PullRequest pullRequest) {
                Double complexityScore = ((pullRequest.getChangedFiles() * 3) + (pullRequest.getCommits() * 0.5)
                                + pullRequest.getAdditions() + pullRequest.getDeletions()) / 10;
                if (complexityScore < 10) {
                        return 1; // Simple
                } else if (complexityScore < 50) {
                        return 3; // Medium
                } else if (complexityScore < 100) {
                        return 7; // Large
                } else if (complexityScore < 500) {
                        return 17; // Huge
                }
                return 33; // Overly complex
        }
}
