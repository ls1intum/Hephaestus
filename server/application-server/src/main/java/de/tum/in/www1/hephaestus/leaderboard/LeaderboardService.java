package de.tum.in.www1.hephaestus.leaderboard;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import de.tum.in.www1.hephaestus.codereview.pullrequest.PullRequest;
import de.tum.in.www1.hephaestus.codereview.pullrequest.review.PullRequestReviewDTO;
import de.tum.in.www1.hephaestus.codereview.user.User;
import de.tum.in.www1.hephaestus.codereview.user.UserService;
import de.tum.in.www1.hephaestus.codereview.user.UserType;

@Service
public class LeaderboardService {
    private static final Logger logger = LoggerFactory.getLogger(LeaderboardService.class);

    private final UserService userService;

    @Value("${monitoring.timeframe}")
    private int timeframe;

    public LeaderboardService(UserService userService) {
        this.userService = userService;
    }

    public List<LeaderboardEntry> createLeaderboard(Optional<LocalDate> after, Optional<LocalDate> before) {
        logger.info("Creating leaderboard dataset");

        LocalDateTime afterCutOff = after.isPresent() ? after.get().atStartOfDay()
                : LocalDate.now().minusDays(timeframe).atStartOfDay();
        Optional<LocalDateTime> beforeCutOff = before.map(date -> date.plusDays(1).atStartOfDay());

        List<User> users = userService.getAllUsersInTimeframe(afterCutOff.atOffset(ZoneOffset.UTC),
                beforeCutOff.map(b -> b.atOffset(ZoneOffset.UTC)).orElse(OffsetDateTime.now()));

        logger.info("Found " + users.size() + " users for the leaderboard");

        List<LeaderboardEntry> leaderboard = users.stream().map(user -> {
            // ignore non-users, e.g. bots
            if (user.getType() != UserType.USER) {
                return null;
            }
            AtomicInteger score = new AtomicInteger(0);
            Set<Integer> reviewedPRs = new HashSet<>();
            Set<PullRequestReviewDTO> changesRequestedSet = new HashSet<>();
            Set<PullRequestReviewDTO> approvedSet = new HashSet<>();
            Set<PullRequestReviewDTO> commentSet = new HashSet<>();

            user.getReviews().stream()
                    .forEach(review -> {
                        if (review.getPullRequest().getAuthor().getLogin().equals(user.getLogin())) {
                            return;
                        }

                        PullRequestReviewDTO reviewDTO = new PullRequestReviewDTO(review.getId(), review.getCreatedAt(),
                                review.getUpdatedAt(), review.getSubmittedAt(), review.getState());

                        reviewedPRs.add(review.getPullRequest().getNumber());
                        switch (review.getState()) {
                            case CHANGES_REQUESTED:
                                changesRequestedSet.add(reviewDTO);
                                break;
                            case APPROVED:
                                approvedSet.add(reviewDTO);
                                break;
                            case COMMENTED:
                                commentSet.add(reviewDTO);
                                break;
                            default:
                                // ignore other states and don't add to score
                                return;
                        }
                        score.addAndGet(calculateScore(review.getPullRequest()));
                    });
            return new LeaderboardEntry(user.getLogin(), user.getAvatarUrl(), user.getName(), user.getType(),
                    score.get(),
                    0, // preliminary rank
                    reviewedPRs.size(),
                    changesRequestedSet.toArray(new PullRequestReviewDTO[changesRequestedSet.size()]),
                    approvedSet.toArray(new PullRequestReviewDTO[approvedSet.size()]),
                    commentSet.toArray(new PullRequestReviewDTO[commentSet.size()]));
        }).filter(Objects::nonNull).collect(Collectors.toCollection(ArrayList::new));

        // update ranks by score
        leaderboard.sort(Comparator.comparingInt(LeaderboardEntry::getScore).reversed());
        AtomicInteger rank = new AtomicInteger(1);
        leaderboard.stream().forEach(entry -> {
            entry.setRank(rank.get());
            rank.incrementAndGet();
        });

        return leaderboard;
    }

    /**
     * Calculates the score for a given pull request.
     * Possible values: 1, 3, 7, 17, 33.
     * Taken from the original leaderboard implementation script.
     * 
     * @param pullRequest
     * @return score
     */
    private int calculateScore(PullRequest pullRequest) {
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
