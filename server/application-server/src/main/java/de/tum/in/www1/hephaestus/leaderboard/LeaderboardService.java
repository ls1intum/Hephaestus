package de.tum.in.www1.hephaestus.leaderboard;

import java.util.List;

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

        List<LeaderboardEntry> leaderboard = users.stream().map(user -> {
            logger.info("Creating leaderboard entry for user: " + user.getLogin());
            return new LeaderboardEntry(user.getLogin(), user.getName(), 0, 0, 0, 0, 0);
        }).toList();

        return leaderboard;
    }
}
