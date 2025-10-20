package de.tum.in.www1.hephaestus.leaderboard;

import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequest;
import de.tum.in.www1.hephaestus.gitprovider.user.User;
import java.time.Instant;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

@Service
@Primary
public class DefaultLeaguePointsCalculationService implements LeaguePointsCalculationService {

    private static final Logger logger = LoggerFactory.getLogger(DefaultLeaguePointsCalculationService.class);

    private static final int DEFAULT_POINTS = LeaguePointsCalculationService.POINTS_DEFAULT;
    private static final int POINTS_THRESHOLD_HIGH = LeaguePointsCalculationService.POINTS_THRESHOLD_HIGH;
    private static final int POINTS_THRESHOLD_LOW = LeaguePointsCalculationService.POINTS_THRESHOLD_LOW;
    private static final int DECAY_MINIMUM = LeaguePointsCalculationService.DECAY_MINIMUM;
    private static final double DECAY_FACTOR = LeaguePointsCalculationService.DECAY_FACTOR;
    private static final double K_FACTOR_NEW_PLAYER = LeaguePointsCalculationService.K_FACTOR_NEW_PLAYER;
    private static final double K_FACTOR_LOW_POINTS = LeaguePointsCalculationService.K_FACTOR_LOW_POINTS;
    private static final double K_FACTOR_MEDIUM_POINTS = LeaguePointsCalculationService.K_FACTOR_MEDIUM_POINTS;
    private static final double K_FACTOR_HIGH_POINTS = LeaguePointsCalculationService.K_FACTOR_HIGH_POINTS;

    @Override
    public int calculateNewPoints(User user, LeaderboardEntryDTO entry) {
        Objects.requireNonNull(user, "user must not be null");
        Objects.requireNonNull(entry, "entry must not be null");

        int storedPoints = user.getLeaguePoints();
        int effectivePoints = storedPoints == 0 ? DEFAULT_POINTS : storedPoints;
        double kFactor = getKFactor(user, effectivePoints);
        int decay = calculateDecay(effectivePoints);
        int performanceBonus = calculatePerformanceBonus(entry.score());
        int placementBonus = calculatePlacementBonus(entry.rank());
        int pointChange = (int) (kFactor * (performanceBonus + placementBonus - decay));
        int newPoints = Math.max(1, effectivePoints + pointChange);

        logger.info(
            "Points calculation: old={}, k={}, decay={}, performanceBonus={}, placement={}, pointchange={}, new={}",
            effectivePoints,
            kFactor,
            decay,
            performanceBonus,
            placementBonus,
            pointChange,
            newPoints
        );

        return newPoints;
    }

    private double getKFactor(User user, int currentPoints) {
        if (isNewPlayer(user)) {
            return K_FACTOR_NEW_PLAYER;
        }
        if (currentPoints < POINTS_THRESHOLD_LOW) {
            return K_FACTOR_LOW_POINTS;
        }
        if (currentPoints < POINTS_THRESHOLD_HIGH) {
            return K_FACTOR_MEDIUM_POINTS;
        }
        return K_FACTOR_HIGH_POINTS;
    }

    private boolean isNewPlayer(User user) {
        Instant thirtyDaysAgo = Instant.now().minusSeconds(30L * 24 * 60 * 60);
        return user.getMergedPullRequests()
            .stream()
            .filter(Objects::nonNull)
            .filter(PullRequest::isMerged)
            .map(PullRequest::getMergedAt)
            .filter(Objects::nonNull)
            .noneMatch(mergedAt -> mergedAt.isBefore(thirtyDaysAgo));
    }

    private int calculateDecay(int currentPoints) {
        if (currentPoints > 0) {
            return Math.max(DECAY_MINIMUM, (int) (currentPoints * DECAY_FACTOR));
        }
        return 0;
    }

    private int calculatePerformanceBonus(int score) {
        return (int) (Math.sqrt((double) score) * 10);
    }

    private int calculatePlacementBonus(int placement) {
        if (placement <= 3) {
            return 20 * (4 - placement);
        }
        return 0;
    }
}