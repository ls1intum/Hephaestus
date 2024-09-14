package de.tum.in.www1.hephaestus.leaderboard;

import com.fasterxml.jackson.annotation.JsonInclude;
import de.tum.in.www1.hephaestus.codereview.user.UserType;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record LeaderboardEntry(String githubName, String avatarUrl, String name, UserType type, int score,
        int changesRequested,
        int approvals, int comments) {
}
