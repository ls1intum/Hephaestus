package de.tum.in.www1.hephaestus.leaderboard.tasks;

import static org.junit.jupiter.api.Assertions.*;

import com.slack.api.model.User;
import de.tum.in.www1.hephaestus.gitprovider.user.UserInfoDTO;
import de.tum.in.www1.hephaestus.leaderboard.LeaderboardEntryDTO;
import de.tum.in.www1.hephaestus.testconfig.BaseUnitTest;
import java.util.List;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Unit tests for Slack user matching logic in SlackWeeklyLeaderboardTask.
 *
 * <p>
 * The matching follows industry best practices for cross-platform identity
 * resolution:
 * <ol>
 * <li><b>Linked Slack User ID</b>: Highest priority - users explicitly link
 * their Slack account</li>
 * <li><b>Email matching</b>: Fallback for users who haven't linked their
 * account</li>
 * </ol>
 *
 * <p>
 * Fuzzy name matching is intentionally NOT supported - names are not reliable
 * identifiers.
 */
class SlackWeeklyLeaderboardTaskMatchingTest extends BaseUnitTest {

    private SlackWeeklyLeaderboardTask task;

    @BeforeEach
    void setUp() {
        task = new SlackWeeklyLeaderboardTask();
    }

    private User createSlackUser(String id, String name, String realName, String email) {
        User user = new User();
        user.setId(id);
        user.setName(name);
        user.setRealName(realName);

        User.Profile profile = new User.Profile();
        profile.setEmail(email);
        user.setProfile(profile);

        return user;
    }

    private LeaderboardEntryDTO createLeaderboardEntry(
        Long id,
        String login,
        String name,
        String email,
        String slackUserId
    ) {
        UserInfoDTO userInfo = new UserInfoDTO(
            id,
            login,
            email,
            "https://example.com/avatar.jpg",
            name,
            "https://github.com/" + login,
            0,
            slackUserId
        );
        return new LeaderboardEntryDTO(1, 100, userInfo, null, List.of(), 0, 0, 0, 0, 0, 0);
    }

    @SuppressWarnings("unchecked")
    private User invokeMapToSlackUser(List<User> slackUsers, LeaderboardEntryDTO entry) {
        Function<LeaderboardEntryDTO, User> mapper = (Function<
                LeaderboardEntryDTO,
                User
            >) ReflectionTestUtils.invokeMethod(task, "mapToSlackUser", slackUsers);
        assertNotNull(mapper);
        return mapper.apply(entry);
    }

    @Nested
    @DisplayName("Linked Slack User ID matching (highest priority)")
    class LinkedSlackUserIdMatching {

        @Test
        @DisplayName("should match by linked Slack User ID")
        void shouldMatchByLinkedSlackUserId() {
            User slackUser = createSlackUser("U12345ABC", "khiem.nguyen", "Khiem Nguyen", "khiem.nguyen@example.com");
            LeaderboardEntryDTO entry = createLeaderboardEntry(
                1L,
                "HawKhiem",
                "Khiem Nguyen",
                "different.email@example.com", // Different email
                "U12345ABC" // Linked Slack ID
            );

            User result = invokeMapToSlackUser(List.of(slackUser), entry);

            assertNotNull(result);
            assertEquals("U12345ABC", result.getId());
        }

        @Test
        @DisplayName("should prefer linked Slack ID over email match")
        void shouldPreferLinkedSlackIdOverEmail() {
            User slackUser1 = createSlackUser("U001", "user1", "User One", "user@example.com");
            User slackUser2 = createSlackUser("U002", "user2", "User Two", "user2@example.com");

            // Entry has linked Slack ID pointing to user1, but email matches user2
            LeaderboardEntryDTO entry = createLeaderboardEntry(
                1L,
                "testuser",
                "Test User",
                "user2@example.com", // Matches slackUser2
                "U001" // But linked to slackUser1
            );

            User result = invokeMapToSlackUser(List.of(slackUser1, slackUser2), entry);

            assertNotNull(result);
            assertEquals("U001", result.getId(), "Should use linked Slack ID, not email match");
        }

        @Test
        @DisplayName("should return null if linked Slack ID doesn't exist in workspace")
        void shouldReturnNullIfLinkedSlackIdNotFound() {
            User slackUser = createSlackUser("U001", "user", "User", "user@example.com");
            LeaderboardEntryDTO entry = createLeaderboardEntry(
                1L,
                "testuser",
                "Test User",
                null,
                "U_NONEXISTENT" // Linked to a user who no longer exists
            );

            User result = invokeMapToSlackUser(List.of(slackUser), entry);

            // Should fall back and still not match (no email)
            assertNull(result);
        }
    }

    @Nested
    @DisplayName("Email matching (fallback)")
    class EmailMatching {

        @Test
        @DisplayName("should match by email when no Slack ID is linked")
        void shouldMatchByEmailWhenNoSlackIdLinked() {
            User slackUser = createSlackUser("U001", "khiem.nguyen", "Khiem Nguyen", "khiem.nguyen@example.com");
            LeaderboardEntryDTO entry = createLeaderboardEntry(
                1L,
                "HawKhiem",
                "Khiem Nguyen",
                "khiem.nguyen@example.com",
                null // No linked Slack ID
            );

            User result = invokeMapToSlackUser(List.of(slackUser), entry);

            assertNotNull(result);
            assertEquals("U001", result.getId());
        }

        @Test
        @DisplayName("should match email case-insensitively")
        void shouldMatchEmailCaseInsensitively() {
            User slackUser = createSlackUser("U001", "user", "User", "Test.User@EXAMPLE.COM");
            LeaderboardEntryDTO entry = createLeaderboardEntry(
                1L,
                "testuser",
                "Test User",
                "test.user@example.com",
                null
            );

            User result = invokeMapToSlackUser(List.of(slackUser), entry);

            assertNotNull(result);
            assertEquals("U001", result.getId());
        }
    }

    @Nested
    @DisplayName("No fuzzy matching (by design)")
    class NoFuzzyMatching {

        @Test
        @DisplayName("should NOT match 'Khiem Nguyen' to 'Khoa Nguyen' - different people with similar names")
        void shouldNotMatchKhiemToKhoa() {
            // This is the critical bug that was fixed
            User khoaSlackUser = createSlackUser("U001", "dakennguyen", "Khoa Nguyen", "khoa.nguyen@example.com");
            LeaderboardEntryDTO khiemEntry = createLeaderboardEntry(
                1L,
                "HawKhiem",
                "Khiem Nguyen", // Similar name to Khoa Nguyen
                "khiem.nguyen@example.com", // Different email
                null // No linked Slack ID
            );

            User result = invokeMapToSlackUser(List.of(khoaSlackUser), khiemEntry);

            assertNull(result, "Should NOT match users with similar names but different emails");
        }

        @Test
        @DisplayName("should NOT match users with same name but different emails")
        void shouldNotMatchSameNameDifferentEmail() {
            User slackUser = createSlackUser("U001", "jsmith", "John Smith", "john.smith@company-a.com");
            LeaderboardEntryDTO entry = createLeaderboardEntry(
                1L,
                "johnsmith",
                "John Smith", // Same name
                "john.smith@company-b.com", // Different email
                null
            );

            User result = invokeMapToSlackUser(List.of(slackUser), entry);

            assertNull(result, "Should NOT match users with same name but different emails");
        }
    }

    @Nested
    @DisplayName("Edge cases")
    class EdgeCases {

        @Test
        @DisplayName("should return null when LeaderboardEntryDTO has null user")
        void shouldReturnNullForNullUser() {
            LeaderboardEntryDTO entry = new LeaderboardEntryDTO(1, 100, null, null, List.of(), 0, 0, 0, 0, 0, 0);
            User slackUser = createSlackUser("U001", "test", "Test User", "test@example.com");

            User result = invokeMapToSlackUser(List.of(slackUser), entry);

            assertNull(result);
        }

        @Test
        @DisplayName("should return null when GitHub email is null and no Slack ID linked")
        void shouldReturnNullWhenNoEmailAndNoSlackId() {
            User slackUser = createSlackUser("U001", "user", "User", "user@example.com");
            LeaderboardEntryDTO entry = createLeaderboardEntry(1L, "user", "User", null, null);

            User result = invokeMapToSlackUser(List.of(slackUser), entry);

            assertNull(result);
        }

        @Test
        @DisplayName("should handle empty Slack user list")
        void shouldHandleEmptySlackUserList() {
            LeaderboardEntryDTO entry = createLeaderboardEntry(
                1L,
                "HawKhiem",
                "Khiem Nguyen",
                "khiem@example.com",
                "U12345"
            );

            User result = invokeMapToSlackUser(List.of(), entry);

            assertNull(result);
        }

        @Test
        @DisplayName("should handle Slack user with null profile")
        void shouldHandleNullProfile() {
            User slackUser = new User();
            slackUser.setId("U001");
            slackUser.setName("user");
            slackUser.setRealName("User");
            slackUser.setProfile(null);

            LeaderboardEntryDTO entry = createLeaderboardEntry(1L, "user", "User", "user@example.com", null);

            User result = invokeMapToSlackUser(List.of(slackUser), entry);

            assertNull(result, "Should handle null profile gracefully");
        }
    }
}
