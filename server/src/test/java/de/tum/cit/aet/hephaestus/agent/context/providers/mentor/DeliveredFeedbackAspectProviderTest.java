package de.tum.cit.aet.hephaestus.agent.context.providers.mentor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.agent.context.ContextRequest;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.User;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.UserRepository;
import de.tum.cit.aet.hephaestus.practices.feedback.Feedback;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackChannel;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackRepository;
import de.tum.cit.aet.hephaestus.practices.model.WorkArtifact;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCache;
import org.springframework.data.domain.Pageable;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class DeliveredFeedbackAspectProviderTest extends BaseUnitTest {

    @Mock
    UserRepository userRepository;

    @Mock
    FeedbackRepository feedbackRepository;

    @Spy
    ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    CacheManager cacheManager;

    @InjectMocks
    DeliveredFeedbackAspectProvider provider;

    @Test
    @DisplayName("no delivered feedback → empty array, totalDelivered=0")
    void emptyDefaults() throws Exception {
        User user = new User();
        user.setLogin("octo");
        when(userRepository.findById(eq(2L))).thenReturn(Optional.of(user));
        when(
            feedbackRepository.findRecentDeliveredForRecipient(eq(1L), eq(2L), any(Instant.class), any(Pageable.class))
        ).thenReturn(List.of());

        Map<String, byte[]> files = new HashMap<>();
        provider.contribute(new ContextRequest.MentorChatRequest(1L, 2L, UUID.randomUUID()), files);

        byte[] bytes = files.get("inputs/context/delivered_feedback.json");
        assertThat(bytes).isNotNull();
        JsonNode root = objectMapper.readTree(bytes);
        assertThat(root.get("user").get("login").asString()).isEqualTo("octo");
        assertThat(root.get("deliveredFeedback").isArray()).isTrue();
        assertThat(root.get("deliveredFeedback")).isEmpty();
        assertThat(root.get("totalDelivered").asLong()).isEqualTo(0L);
    }

    @Test
    @DisplayName("ships the rendered body + skips a delivered unit with a blank body")
    void shipsRenderedBodyAndSkipsBlank() throws Exception {
        User user = new User();
        user.setLogin("octo");
        when(userRepository.findById(eq(2L))).thenReturn(Optional.of(user));

        Feedback withBody = Feedback.builder()
            .channel(FeedbackChannel.IN_CONTEXT)
            .artifactType(WorkArtifact.PULL_REQUEST)
            .artifactId(575L)
            .deliveredAt(Instant.parse("2026-06-17T08:30:00Z"))
            .body("Nice work scoping this PR — one thing to tighten before merge.")
            .build();
        Feedback blank = Feedback.builder()
            .channel(FeedbackChannel.IN_CONTEXT)
            .artifactType(WorkArtifact.ISSUE)
            .artifactId(574L)
            .deliveredAt(Instant.parse("2026-06-16T08:30:00Z"))
            .body("   ")
            .build();
        when(
            feedbackRepository.findRecentDeliveredForRecipient(eq(1L), eq(2L), any(Instant.class), any(Pageable.class))
        ).thenReturn(List.of(withBody, blank));

        Map<String, byte[]> files = new HashMap<>();
        provider.contribute(new ContextRequest.MentorChatRequest(1L, 2L, UUID.randomUUID()), files);

        JsonNode root = objectMapper.readTree(files.get("inputs/context/delivered_feedback.json"));
        // The blank-body unit is dropped — the mentor never quotes an empty "your feedback".
        assertThat(root.get("totalDelivered").asLong()).isEqualTo(1L);
        JsonNode only = root.get("deliveredFeedback").get(0);
        assertThat(only.get("body").asString()).contains("Nice work scoping this PR");
        assertThat(only.get("surface").asString()).isEqualTo("IN_CONTEXT");
        assertThat(only.get("artifactType").asString()).isEqualTo("PULL_REQUEST");
        assertThat(only.get("artifactId").asLong()).isEqualTo(575L);
    }

    @Test
    @DisplayName(
        "omits artifactType/artifactId for a reflection-style unit with no artifact, still ships surface + body"
    )
    void omitsNullArtifactFields() throws Exception {
        User user = new User();
        user.setLogin("octo");
        when(userRepository.findById(eq(2L))).thenReturn(Optional.of(user));

        // A reflection/profile-style delivered unit legitimately carries no artifactType/artifactId
        // (Feedback allows them null). The null-omission contract: those JSON fields are absent, but the
        // mentor still gets the surface + body it quotes.
        Feedback noArtifact = Feedback.builder()
            .channel(FeedbackChannel.IN_CONTEXT)
            .deliveredAt(Instant.parse("2026-06-17T08:30:00Z"))
            .body("Reflecting on your last few reviews — here is a pattern to watch.")
            .build();
        when(
            feedbackRepository.findRecentDeliveredForRecipient(eq(1L), eq(2L), any(Instant.class), any(Pageable.class))
        ).thenReturn(List.of(noArtifact));

        Map<String, byte[]> files = new HashMap<>();
        provider.contribute(new ContextRequest.MentorChatRequest(1L, 2L, UUID.randomUUID()), files);

        JsonNode root = objectMapper.readTree(files.get("inputs/context/delivered_feedback.json"));
        assertThat(root.get("totalDelivered").asLong()).isEqualTo(1L);
        JsonNode only = root.get("deliveredFeedback").get(0);
        assertThat(only.has("artifactType")).isFalse();
        assertThat(only.has("artifactId")).isFalse();
        assertThat(only.get("surface").asString()).isEqualTo("IN_CONTEXT");
        assertThat(only.get("body").asString()).contains("Reflecting on your last few reviews");
    }

    @Test
    @DisplayName("warm cache: a second turn with the same key reuses the payload without rebuilding")
    void warmCacheReusesPayload() throws Exception {
        // Stub a REAL cache so the compute-if-absent branch is exercised — without this, getCache returns null
        // and only the uncached path runs (the unregistered-cache bug this test would have caught).
        when(cacheManager.getCache(eq("mentor_delivered_feedback_aspect"))).thenReturn(
            new ConcurrentMapCache("mentor_delivered_feedback_aspect")
        );
        User user = new User();
        user.setLogin("octo");
        when(userRepository.findById(eq(2L))).thenReturn(Optional.of(user));
        when(
            feedbackRepository.findRecentDeliveredForRecipient(eq(1L), eq(2L), any(Instant.class), any(Pageable.class))
        ).thenReturn(List.of());

        var request = new ContextRequest.MentorChatRequest(1L, 2L, UUID.randomUUID());
        provider.contribute(request, new HashMap<>());
        provider.contribute(request, new HashMap<>());

        // The query (the heart of buildPayload) ran once; the second turn hit the warm cache.
        verify(feedbackRepository, times(1)).findRecentDeliveredForRecipient(
            eq(1L),
            eq(2L),
            any(Instant.class),
            any(Pageable.class)
        );
    }
}
