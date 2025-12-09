package de.tum.in.www1.hephaestus.leaderboard;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "hephaestus.leaderboard")
public class LeaderboardProperties {

    private final List<String> selfReviewAuthorLogins = new ArrayList<>(List.of("Copilot"));

    public List<String> getSelfReviewAuthorLogins() {
        return selfReviewAuthorLogins;
    }
}
