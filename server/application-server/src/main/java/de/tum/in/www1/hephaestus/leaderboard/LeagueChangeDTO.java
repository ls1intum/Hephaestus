package de.tum.in.www1.hephaestus.leaderboard;

public record LeagueChangeDTO(
    String login,
    int leaguePointsChange
) {}