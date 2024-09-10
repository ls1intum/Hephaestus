package de.tum.in.www1.hephaestus.leaderboard;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record LeaderboardEntry(String githubName, String name, int score, int total, int changesRequested,
        int approvals, int comments) {
}
