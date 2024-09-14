package de.tum.in.www1.hephaestus.leaderboard;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import de.tum.in.www1.hephaestus.codereview.pullrequest.PullRequest;
import de.tum.in.www1.hephaestus.codereview.user.User;
import de.tum.in.www1.hephaestus.codereview.user.UserService;

@Service
public class LeaderboardService {
    private static final Logger logger = LoggerFactory.getLogger(LeaderboardService.class);

    private final UserService userService;

    public LeaderboardService(UserService userService) {
        this.userService = userService;
    }

    public List<LeaderboardEntry> createLeaderboard() {
        logger.info("Creating leaderboard dataset");

        List<User> users = userService.getAllUsers();
        logger.info("Found " + users.size() + " users");

        List<LeaderboardEntry> leaderboard = users.stream().map(user -> {
            int comments = user.getIssueComments().size();
            AtomicInteger changesRequested = new AtomicInteger(0);
            AtomicInteger changesApproved = new AtomicInteger(0);
            AtomicInteger score = new AtomicInteger(0);
            user.getReviews().stream().forEach(review -> {
                switch (review.getState()) {
                    case CHANGES_REQUESTED:
                        changesRequested.incrementAndGet();
                        break;
                    case APPROVED:
                        changesApproved.incrementAndGet();
                        break;
                    default:
                        break;
                }
                score.addAndGet(calculateScore(review.getPullRequest()));
            });
            return new LeaderboardEntry(user.getLogin(), user.getAvatarUrl(), user.getName(), user.getType(),
                    score.get(),
                    changesRequested.get(),
                    changesApproved.get(), comments);
        }).toList();

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
        Double complexityScore = (pullRequest.getChangedFiles() * 3) + (pullRequest.getCommits() * 0.5)
                + pullRequest.getAdditions() + pullRequest.getDeletions();
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
