package de.tum.in.www1.hephaestus.leaderboard;

import static de.tum.in.www1.hephaestus.shared.LeaguePointsConstants.*;

import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequest;
import de.tum.in.www1.hephaestus.gitprovider.user.User;
import java.time.Instant;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Service for calculating league point updates based on leaderboard performance.
 *
 * <p>Uses an ELO-like algorithm with:
 * <ul>
 *   <li>K-factor adjustments for new vs established players</li>
 *   <li>Decay to prevent point hoarding</li>
 *   <li>Performance bonuses based on XP score</li>
 *   <li>Placement bonuses for top ranks</li>
 * </ul>
 *
 * <p>All constants are defined in {@link de.tum.in.www1.hephaestus.shared.LeaguePointsConstants}.
 */
@Service
public class LeaguePointsService {

    private static final Logger log = LoggerFactory.getLogger(LeaguePointsService.class);

    /**
     * Calculates updated league points for a user based on their leaderboard entry.
     *
     * @param user the user to calculate points for (must have merged PRs loaded)
     * @param currentLeaguePoints current league point total
     * @param entry the leaderboard entry with rank and score
     * @return new league point total (minimum 1)
     */
    public int calculateNewPoints(User user, int currentLeaguePoints, LeaderboardEntryDTO entry) {
        Objects.requireNonNull(user, "user must not be null");
        Objects.requireNonNull(entry, "entry must not be null");

        int effectivePoints = currentLeaguePoints == 0 ? POINTS_DEFAULT : currentLeaguePoints;
        double kFactor = getKFactor(user, effectivePoints);
        int decay = calculateDecay(effectivePoints);
        int performanceBonus = calculatePerformanceBonus(entry.score());
        int placementBonus = calculatePlacementBonus(entry.rank());
        int pointChange = (int) (kFactor * (performanceBonus + placementBonus - decay));
        int newPoints = Math.max(1, effectivePoints + pointChange);

        log.debug(
            "Calculated league points: userLogin={}, oldPoints={}, kFactor={}, decay={}, performanceBonus={}, placementBonus={}, pointChange={}, newPoints={}",
            user.getLogin(),
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
        Instant thresholdTime = Instant.now().minusSeconds(NEW_PLAYER_THRESHOLD_SECONDS);
        return user
            .getMergedPullRequests()
            .stream()
            .filter(Objects::nonNull)
            .filter(PullRequest::isMerged)
            .map(PullRequest::getMergedAt)
            .filter(Objects::nonNull)
            .noneMatch(mergedAt -> mergedAt.isBefore(thresholdTime));
    }

    private int calculateDecay(int currentPoints) {
        if (currentPoints > 0) {
            return Math.max(DECAY_MINIMUM, (int) (currentPoints * DECAY_FACTOR));
        }
        return 0;
    }

    private int calculatePerformanceBonus(int score) {
        return (int) (Math.sqrt((double) score) * PERFORMANCE_SCORE_MULTIPLIER);
    }

    private int calculatePlacementBonus(int placement) {
        if (placement <= PLACEMENT_BONUS_THRESHOLD) {
            return PLACEMENT_BONUS_PER_POSITION * (PLACEMENT_BONUS_THRESHOLD + 1 - placement);
        }
        return 0;
    }
}
