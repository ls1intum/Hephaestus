package de.tum.cit.aet.hephaestus.leaderboard;

import org.jspecify.annotations.NonNull;

public record LeagueChangeDTO(@NonNull String login, @NonNull Integer leaguePointsChange) {}
