package de.tum.in.www1.hephaestus.leaderboard;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.hephaestus.gitprovider.pullrequestreview.PullRequestReviewDTO;
import de.tum.in.www1.hephaestus.gitprovider.user.User;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
@AllArgsConstructor
@Getter
@Setter
@ToString
public class LeaderboardEntry {
        private String githubName;
        private String avatarUrl;
        private String name;
        private User.Type type;
        private int score;
        private int rank;
        private int numberOfReviewedPRs;
        private PullRequestReviewDTO[] changesRequested;
        private PullRequestReviewDTO[] approvals;
        private PullRequestReviewDTO[] comments;
}
