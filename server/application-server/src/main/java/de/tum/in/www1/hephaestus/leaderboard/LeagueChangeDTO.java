package de.tum.in.www1.hephaestus.leaderboard;

import org.springframework.lang.NonNull;

public record LeagueChangeDTO(
    @NonNull String login,
    @NonNull Integer leaguePointsChange
) {}