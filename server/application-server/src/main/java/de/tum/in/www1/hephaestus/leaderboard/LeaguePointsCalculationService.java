package de.tum.in.www1.hephaestus.leaderboard;

import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequest;
import de.tum.in.www1.hephaestus.gitprovider.user.User;
import java.time.OffsetDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class LeaguePointsCalculationService {
    private final Logger logger = LoggerFactory.getLogger(LeaguePointsCalculationService.class);

    public int calculateNewPoints(User user, LeaderboardEntryDTO entry) {
        // Initialize new players with 1000 points
        if (user.getLeaguePoints() == 0) {
            user.setLeaguePoints(1000);
        }

        int oldPoints = user.getLeaguePoints();

        double kFactor = getKFactor(user);
        // Base decay
        int decay = calculateDecay(user.getLeaguePoints());
        // Bonus based on leaderboard score
        int performanceBonus = calculatePerformanceBonus(entry.score());
        // Additional bonus for placements
        int placementBonus = calculatePlacementBonus(entry.rank());
        // Calculate final point change
        int pointChange = (int) (kFactor * (performanceBonus + placementBonus - decay));
        // Apply minimum change to prevent extreme swings
        int newPoints = Math.max(0, oldPoints + pointChange);
        
        logger.info("Points calculation: old={}, k={} decay={}, performanceBonus={}, placement={}, pointchange={}, new={}", 
            oldPoints, kFactor, decay, performanceBonus, placementBonus, pointChange, newPoints);
        
        return newPoints;
    }

    /**
     * Calculate the K factor for the user based on their current points and if they are a new player.
     * @param user
     * @return
     * @see <a href="https://en.wikipedia.org/wiki/Elo_rating_system#Most_accurate_K-factor">Wikipedia: Most accurate K-factor</a>
     */
    private double getKFactor(User user) {
        if (isNewPlayer(user)) {
            return 2.0;
        } else if (user.getLeaguePoints() < 1400) {
            return 1.5;
        } else if (user.getLeaguePoints() < 1800) {
            return 1.2;
        } else {
            return 1.1;
        }
    }

    /**
     * Check if the user's earliest merged pull request is within the last 30 days.
     * @param user
     * @return
     */
    private boolean isNewPlayer(User user) {
        return user.getMergedPullRequests().stream()
            .filter(PullRequest::isMerged)
            .map(PullRequest::getMergedAt)
            .anyMatch(date -> date.isAfter(OffsetDateTime.now().minusDays(30)));
    }
    
    /**
     * Calculate the base decay in points based on the current points.
     * @param currentPoints
     * @return
     */
    private int calculateDecay(int currentPoints) {
        // 5% decay of current points, minimum 10 points if they have any points
        return currentPoints > 0 ? Math.max(10, (int)(currentPoints * 0.05)) : 0;
    }
    
    private int calculatePerformanceBonus(int score) {
        // Convert leaderboard score directly to points with diminishing returns
        return (int)(Math.sqrt(score) * 10);
    }

    private int calculatePlacementBonus(int placement) {
        // Bonus for top 3 placements
        return placement <= 3 ? 20 * (4 - placement) : 0;
    }
}
