package de.tum.in.www1.hephaestus.leaderboard;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

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

        List<LeaderboardEntry> leaderboard = users.parallelStream().map(user -> {
            logger.info("Creating leaderboard entry for user: " + user.getLogin());
            AtomicInteger changesRequested = new AtomicInteger(0);
            AtomicInteger changesApproved = new AtomicInteger(0);
            AtomicInteger comments = new AtomicInteger(0);
            user.getReviews().parallelStream().forEach(review -> {
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
            });
            comments.addAndGet(user.getIssueComments().size());
            return new LeaderboardEntry(user.getLogin(), user.getName(), 0, 0, changesRequested.get(),
                    changesApproved.get(), comments.get());
        }).toList();

        return leaderboard;
    }
}
