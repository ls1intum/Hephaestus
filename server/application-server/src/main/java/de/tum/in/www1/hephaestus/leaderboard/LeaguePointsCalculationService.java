package de.tum.in.www1.hephaestus.leaderboard;

import static de.tum.in.www1.hephaestus.shared.LeaguePointsConstants.*;

import de.tum.in.www1.hephaestus.gitprovider.user.User;

/**
 * Service for calculating league points updates.
 *
 * <p>Uses constants from {@link de.tum.in.www1.hephaestus.shared.LeaguePointsConstants}
 * to ensure consistency between workspace initialization and leaderboard calculations.
 */
public interface LeaguePointsCalculationService {
    int calculateNewPoints(User user, int currentLeaguePoints, LeaderboardEntryDTO entry);
}
