package de.tum.in.www1.hephaestus.leaderboard;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Leaderboard scoring configuration. Excludes reviews from scoring when the PR author
 * matches a configured login (e.g., Copilot) and the reviewer is an assignee.
 */
@Component
@ConfigurationProperties(prefix = "hephaestus.leaderboard")
public class LeaderboardProperties {

    private List<String> selfReviewAuthorLogins = new ArrayList<>();

    public List<String> getSelfReviewAuthorLogins() {
        return selfReviewAuthorLogins;
    }

    public void setSelfReviewAuthorLogins(List<String> selfReviewAuthorLogins) {
        this.selfReviewAuthorLogins = selfReviewAuthorLogins != null ? selfReviewAuthorLogins : new ArrayList<>();
    }
}
