package de.tum.in.www1.hephaestus.leaderboard;

import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequest;
import de.tum.in.www1.hephaestus.gitprovider.user.User;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class LeaguePointsCalculationService {

    private final Logger logger = LoggerFactory.getLogger(LeaguePointsCalculationService.class);

    // Starting points for new players
    public static int POINTS_DEFAULT = 1000;
    // Upper bound for first reducting in the k-factor
    public static int POINTS_THRESHOLD_HIGH = 1750;
    // Lower bound for first reducting in the k-factor
    public static int POINTS_THRESHOLD_LOW = 1250;
    // Minimum amount of points to decay each cycle
    public static int DECAY_MINIMUM = 10;
    // Factor to determine how much of the current points are decayed each cycle
    public static double DECAY_FACTOR = 0.05;
    // K-factors depending on the player's league points
    public static double K_FACTOR_NEW_PLAYER = 2.0;
    public static double K_FACTOR_LOW_POINTS = 1.5;
    public static double K_FACTOR_MEDIUM_POINTS = 1.2;
    public static double K_FACTOR_HIGH_POINTS = 1.1;

    public int calculateNewPoints(User user, LeaderboardEntryDTO entry) {
        // Initialize points for new players
        if (user.getLeaguePoints() == 0) {
            user.setLeaguePoints(POINTS_DEFAULT);
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
        int newPoints = Math.max(1, oldPoints + pointChange);

        logger.info(
            "Points calculation: old={}, k={}, decay={}, performanceBonus={}, placement={}, pointchange={}, new={}",
            oldPoints,
            kFactor,
            decay,
            performanceBonus,
            placementBonus,
            pointChange,
            newPoints
        );

        return newPoints;
    }

    /**
     * Calculate the K factor for the user based on their current points and if they are a new player.
     * The K-factor is used to control the sensitivity of the rating system to changes in the leaderboard.
     * New players have a higher K-factor to allow them to quickly reach their true skill level.
     * @param user
     * @return K factor
     * @see <a href="https://en.wikipedia.org/wiki/Elo_rating_system#Most_accurate_K-factor">Wikipedia: Most accurate K-factor</a>
     */
    private double getKFactor(User user) {
        if (isNewPlayer(user)) {
            return K_FACTOR_NEW_PLAYER;
        } else if (user.getLeaguePoints() < POINTS_THRESHOLD_LOW) {
            return K_FACTOR_LOW_POINTS;
        } else if (user.getLeaguePoints() < POINTS_THRESHOLD_HIGH) {
            return K_FACTOR_MEDIUM_POINTS;
        } else {
            return K_FACTOR_HIGH_POINTS;
        }
    }

    /**
     * Check if the user's earliest merged pull request is within the last 30 days.
     * @param user
     * @return true if the pull request is within the last 30 days
     */
    private boolean isNewPlayer(User user) {
        return user
            .getMergedPullRequests()
            .stream()
            .filter(PullRequest::isMerged)
            .map(PullRequest::getMergedAt)
            .noneMatch(date -> date.isAfter(Instant.now().minusSeconds(30L * 24 * 60 * 60)));
    }

    /**
     * Calculate the base decay in points based on the current points.
     * @param currentPoints Current amount of league points
     * @return Amount of decay points
     */
    private int calculateDecay(int currentPoints) {
        // decay a part of the current points, at least DECAY_MINIMUM points
        return currentPoints > 0 ? Math.max(DECAY_MINIMUM, (int) (currentPoints * DECAY_FACTOR)) : 0;
    }

    /**
     * Calculate the bonus points based on the leaderboard score.
     * @param score Leaderboard score
     * @return Bonus points
     */
    private int calculatePerformanceBonus(int score) {
        // Convert leaderboard score directly to points with diminishing returns
        return (int) (Math.sqrt(score) * 10);
    }

    /**
     * Calculate the bonus points based on the placement in the leaderboard.
     * @param placement Placement in the leaderboard
     * @return Bonus points
     */
    private int calculatePlacementBonus(int placement) {
        // Bonus for top 3 placements
        return placement <= 3 ? 20 * (4 - placement) : 0;
    }
}
