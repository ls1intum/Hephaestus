package de.tum.in.www1.hephaestus.leaderboard;

import de.tum.in.www1.hephaestus.gitprovider.user.User;

public interface LeaguePointsCalculationService {
    int POINTS_DEFAULT = 1000;
    int POINTS_THRESHOLD_HIGH = 1750;
    int POINTS_THRESHOLD_LOW = 1250;
    int DECAY_MINIMUM = 10;
    double DECAY_FACTOR = 0.05;
    double K_FACTOR_NEW_PLAYER = 2.0;
    double K_FACTOR_LOW_POINTS = 1.5;
    double K_FACTOR_MEDIUM_POINTS = 1.2;
    double K_FACTOR_HIGH_POINTS = 1.1;

    int calculateNewPoints(User user, LeaderboardEntryDTO entry);
}
