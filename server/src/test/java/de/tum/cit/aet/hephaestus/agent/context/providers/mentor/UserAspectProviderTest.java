package de.tum.cit.aet.hephaestus.agent.context.providers.mentor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.agent.context.ContextRequest;
import de.tum.cit.aet.hephaestus.agent.context.providers.mentor.UserAspectProvider.ActivityInsights;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.User;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.UserRepository;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.springframework.cache.CacheManager;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

@DisplayName("UserAspectProvider")
class UserAspectProviderTest extends BaseUnitTest {

    @Mock
    UserRepository userRepository;

    @Mock
    MentorAspectQueryRepository queryRepository;

    @Spy
    ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    CacheManager cacheManager;

    @InjectMocks
    UserAspectProvider provider;

    @Test
    @DisplayName("contribute writes user.json under context/target/ with all expected keys")
    void writesUserJson() throws Exception {
        User user = new User();
        user.setLogin("octo");
        user.setName("Octo Cat");
        when(userRepository.findById(eq(2L))).thenReturn(Optional.of(user));
        when(
            queryRepository.fetchUserCounts(eq(1L), eq(2L), any(Instant.class), any(Instant.class), any(Instant.class))
        ).thenReturn(
            new MentorUserCounts(
                /* openPRs */ 2L,
                /* mergedThisWeek */ 5L,
                /* mergedLastWeek */ 3L,
                /* openIssues */ 1L,
                /* reviewsGivenThisWeek */ 8L,
                /* reviewsGivenLastWeek */ 4L,
                /* reviewsReceivedThisWeek */ 7L,
                /* pendingReviewRequests */ 2L,
                /* unresolvedThreads */ 1L
            )
        );

        Map<String, byte[]> files = new HashMap<>();
        provider.contribute(new ContextRequest.MentorChatRequest(1L, 2L, UUID.randomUUID()), files);

        byte[] bytes = files.get("context/target/user.json");
        assertThat(bytes).isNotNull();
        JsonNode root = objectMapper.readTree(bytes);
        assertThat(root.get("user").get("login").asText()).isEqualTo("octo");
        assertThat(root.get("thisWeek").get("prsOpen").asLong()).isEqualTo(2L);
        assertThat(root.get("thisWeek").get("prsMerged").asLong()).isEqualTo(5L);
        assertThat(root.get("lastWeek").get("prsMerged").asLong()).isEqualTo(3L);
        assertThat(root.get("insights").isArray()).isTrue();
        assertThat(root.get("suggestedReflectionTopics").isArray()).isTrue();
    }

    @Test
    @DisplayName("insights: shipping velocity up surfaces increase message")
    void velocityUp() {
        ActivityInsights insights = UserAspectProvider.generateInsights(0, 5, 2, 3, 3, 0, 0);
        assertThat(insights.insights()).anyMatch(s -> s.contains("velocity increased"));
        assertThat(insights.reflectionTopics()).isEmpty();
    }

    @Test
    @DisplayName("insights: shipping velocity down surfaces reflection topic")
    void velocityDown() {
        ActivityInsights insights = UserAspectProvider.generateInsights(0, 1, 5, 3, 3, 0, 0);
        assertThat(insights.insights()).anyMatch(s -> s.contains("Shipping slowed"));
        assertThat(insights.reflectionTopics()).anyMatch(s -> s.contains("shipping pace"));
    }

    @Test
    @DisplayName("insights: too many open PRs nudges focus")
    void manyOpenPrs() {
        ActivityInsights insights = UserAspectProvider.generateInsights(10, 0, 0, 0, 0, 0, 0);
        assertThat(insights.insights()).anyMatch(s -> s.contains("open PRs"));
        assertThat(insights.reflectionTopics()).anyMatch(s -> s.contains("merge-ready"));
    }

    @Test
    @DisplayName("insights: empty signal falls back to steady-week message")
    void steadyFallback() {
        ActivityInsights insights = UserAspectProvider.generateInsights(0, 0, 0, 0, 0, 0, 0);
        assertThat(insights.insights()).containsExactly("Steady week with consistent activity.");
        assertThat(insights.reflectionTopics()).isEmpty();
    }

    @Test
    @DisplayName("insights: no reviews this week when active last week surfaces nudge")
    void reviewDrop() {
        ActivityInsights insights = UserAspectProvider.generateInsights(0, 0, 0, 0, 3, 0, 0);
        assertThat(insights.insights()).anyMatch(s -> s.contains("No reviews given this week"));
    }
}
