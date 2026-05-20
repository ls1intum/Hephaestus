package de.tum.cit.aet.hephaestus.leaderboard;

import org.springframework.lang.NonNull;

public record LeagueChangeDTO(@NonNull String login, @NonNull Integer leaguePointsChange) {}
