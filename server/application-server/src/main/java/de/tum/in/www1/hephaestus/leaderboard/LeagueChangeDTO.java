package de.tum.in.www1.hephaestus.leaderboard;

import io.micrometer.common.lang.NonNull;

public record LeagueChangeDTO(@NonNull String login, @NonNull Integer leaguePointsChange) {}
