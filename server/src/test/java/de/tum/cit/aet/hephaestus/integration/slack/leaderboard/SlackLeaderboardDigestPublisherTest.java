package de.tum.cit.aet.hephaestus.integration.slack.leaderboard;

import static org.assertj.core.api.Assertions.assertThat;

import com.slack.api.model.User;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.util.List;
import org.junit.jupiter.api.Test;

class SlackLeaderboardDigestPublisherTest extends BaseUnitTest {

    private static User user(String id, String realName, String handle) {
        User u = new User();
        u.setId(id);
        u.setRealName(realName);
        u.setName(handle);
        return u;
    }

    @Test
    void matchesExactRealName() {
        User alice = user("U1", "Alice Smith", "asmith");
        User bob = user("U2", "Bob Jones", "bjones");
        assertThat(SlackLeaderboardDigestPublisher.nearestNameMatch("Alice Smith", List.of(alice, bob))).isEqualTo(
            alice
        );
    }

    @Test
    void matchesWithinEditBudget() {
        User alice = user("U1", "Alice Smith", "asmith");
        // One transposed/changed character is within the budget for an 11-char name.
        assertThat(SlackLeaderboardDigestPublisher.nearestNameMatch("Alice Smyth", List.of(alice))).isEqualTo(alice);
    }

    @Test
    void returnsNullWhenNoCandidateIsCloseEnough() {
        User alice = user("U1", "Alice Smith", "asmith");
        User bob = user("U2", "Bob Jones", "bjones");
        // "Zachariah Wong" is far from every candidate — better to mention no one than the wrong person.
        assertThat(SlackLeaderboardDigestPublisher.nearestNameMatch("Zachariah Wong", List.of(alice, bob))).isNull();
    }

    @Test
    void skipsCandidatesWithNullNamesWithoutThrowing() {
        User ghost = new User(); // no realName, no name — must not trip Levenshtein
        User alice = user("U1", "Alice Smith", "asmith");
        assertThat(SlackLeaderboardDigestPublisher.nearestNameMatch("Alice Smith", List.of(ghost, alice))).isEqualTo(
            alice
        );
    }

    @Test
    void returnsNullForBlankTarget() {
        User alice = user("U1", "Alice Smith", "asmith");
        assertThat(SlackLeaderboardDigestPublisher.nearestNameMatch("  ", List.of(alice))).isNull();
        assertThat(SlackLeaderboardDigestPublisher.nearestNameMatch(null, List.of(alice))).isNull();
    }
}
