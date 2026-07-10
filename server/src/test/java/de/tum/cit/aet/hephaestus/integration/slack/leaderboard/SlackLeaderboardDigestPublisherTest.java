package de.tum.cit.aet.hephaestus.integration.slack.leaderboard;

import static org.assertj.core.api.Assertions.assertThat;

import com.slack.api.model.User;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.UserInfoDTO;
import de.tum.cit.aet.hephaestus.leaderboard.LeaderboardEntryDTO;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Exact-match-only Slack user resolution for the weekly digest. There is deliberately no fuzzy /
 * edit-distance matching: a near-miss must never {@code @}-mention an unrelated person in a public
 * channel. A reviewer with no Slack account is rendered as a plain name, never dropped.
 */
@Tag("unit")
class SlackLeaderboardDigestPublisherTest extends BaseUnitTest {

    private static User slackUser(String id, String handle, String email) {
        User u = new User();
        u.setId(id);
        u.setName(handle);
        User.Profile profile = new User.Profile();
        profile.setEmail(email);
        u.setProfile(profile);
        return u;
    }

    private static LeaderboardEntryDTO entry(String name, String email) {
        UserInfoDTO user = new UserInfoDTO(
            1L,
            name,
            email,
            "https://example.com/a.png",
            name,
            "https://example.com/" + name,
            0
        );
        return new LeaderboardEntryDTO(1, 10, user, null, List.of(), 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
    }

    @Test
    void mentionsByExactHandle() {
        User alice = slackUser("U1", "Alice Smith", "alice@x.io");
        User bob = slackUser("U2", "bjones", "bob@x.io");
        assertThat(
            SlackLeaderboardDigestPublisher.mentionFor(entry("Alice Smith", "none@x.io"), List.of(alice, bob))
        ).isEqualTo("<@U1>");
    }

    @Test
    void doesNotMentionByEmail() {
        User alice = slackUser("U1", "asmith", "alice@x.io");
        assertThat(
            SlackLeaderboardDigestPublisher.mentionFor(entry("Alice Smith", "ALICE@x.io"), List.of(alice))
        ).isEqualTo("Alice Smith");
    }

    @Test
    void rendersPlainNameWhenNoSlackMatch() {
        User bob = slackUser("U2", "bjones", "bob@x.io");
        assertThat(
            SlackLeaderboardDigestPublisher.mentionFor(entry("Zachariah Wong", "zw@x.io"), List.of(bob))
        ).isEqualTo("Zachariah Wong");
    }

    @Test
    void doesNotMentionNearMiss() {
        User alice = slackUser("U1", "asmith", "alice@x.io");
        assertThat(
            SlackLeaderboardDigestPublisher.mentionFor(entry("Alice Smyth", "smyth@x.io"), List.of(alice))
        ).isEqualTo("Alice Smyth");
    }

    @Test
    void returnsNullForTeamRow() {
        LeaderboardEntryDTO teamRow = new LeaderboardEntryDTO(
            1,
            10,
            null,
            null,
            List.of(),
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0
        );
        assertThat(SlackLeaderboardDigestPublisher.mentionFor(teamRow, List.of())).isNull();
    }
}
