package de.tum.cit.aet.hephaestus.agent.context.providers.mentor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.agent.context.ContextRequest;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.User;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.UserRepository;
import de.tum.cit.aet.hephaestus.practices.model.Presence;
import de.tum.cit.aet.hephaestus.practices.model.Severity;
import de.tum.cit.aet.hephaestus.practices.observation.ObservationRepository;
import de.tum.cit.aet.hephaestus.practices.observation.ObservationRepository.PresenceCount;
import de.tum.cit.aet.hephaestus.practices.observation.ObservationRepository.SeverityCount;
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
import org.springframework.data.domain.Pageable;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class ObservationHistoryContentSourceTest extends BaseUnitTest {

    @Mock
    UserRepository userRepository;

    @Mock
    ObservationRepository findingRepository;

    @Mock
    MentorContextQueryRepository queryRepository;

    // No conversation rows in these fixtures, so the gate's default empty allow-set (Mockito ReturnsEmptyValues)
    // is exactly right — every observation here is PR/ISSUE-derived and passes through ungated.
    @Mock
    ConversationConsentGate conversationConsentGate;

    @Spy
    ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    CacheManager cacheManager;

    @InjectMocks
    ObservationHistoryContentSource provider;

    @Test
    void emptyDefaults() throws Exception {
        User user = new User();
        user.setLogin("octo");
        when(userRepository.findById(eq(2L))).thenReturn(Optional.of(user));
        when(
            findingRepository.findRecentByDeveloperAndWorkspace(eq(2L), eq(1L), any(Instant.class), any(Pageable.class))
        ).thenReturn(List.of());
        when(findingRepository.countByPresenceForDeveloper(eq(2L), eq(1L), any(Instant.class))).thenReturn(List.of());
        when(findingRepository.countBySeverityForDeveloper(eq(2L), eq(1L), any(Instant.class))).thenReturn(List.of());
        when(
            queryRepository.findReviewsReceivedSince(eq(1L), eq(2L), any(Instant.class), any(Pageable.class))
        ).thenReturn(List.of());

        Map<String, byte[]> files = new HashMap<>();
        provider.contribute(new ContextRequest.MentorChatRequest(1L, 2L, UUID.randomUUID()), files);

        byte[] bytes = files.get("inputs/context/findings_history.json");
        assertThat(bytes).isNotNull();
        JsonNode root = objectMapper.readTree(bytes);
        assertThat(root.get("user").get("login").asString()).isEqualTo("octo");
        assertThat(root.get("summary").get("totalObservations").asLong()).isEqualTo(0L);
        // All presence states present even when count is 0 — keeps the wire shape stable.
        for (Presence v : Presence.values()) {
            assertThat(root.get("summary").get("byPresence").has(v.name())).isTrue();
        }
        for (Severity s : Severity.values()) {
            assertThat(root.get("summary").get("bySeverity").has(s.name())).isTrue();
        }
        assertThat(root.get("recentObservations").isArray()).isTrue();
        assertThat(root.get("reviewsReceived").isArray()).isTrue();
    }

    @Test
    @DisplayName("aggregations: observation + severity counts populate the summary")
    void aggregationsPopulated() throws Exception {
        User user = new User();
        user.setLogin("octo");
        when(userRepository.findById(eq(2L))).thenReturn(Optional.of(user));
        when(
            findingRepository.findRecentByDeveloperAndWorkspace(eq(2L), eq(1L), any(Instant.class), any(Pageable.class))
        ).thenReturn(List.of());
        PresenceCount positive = mockObservationCount(Presence.PRESENT, 3L);
        PresenceCount negative = mockObservationCount(Presence.ABSENT, 1L);
        when(findingRepository.countByPresenceForDeveloper(eq(2L), eq(1L), any(Instant.class))).thenReturn(
            List.of(positive, negative)
        );
        SeverityCount major = mockSeverityCount(Severity.MAJOR, 2L);
        when(findingRepository.countBySeverityForDeveloper(eq(2L), eq(1L), any(Instant.class))).thenReturn(
            List.of(major)
        );
        when(
            queryRepository.findReviewsReceivedSince(eq(1L), eq(2L), any(Instant.class), any(Pageable.class))
        ).thenReturn(List.of());

        Map<String, byte[]> files = new HashMap<>();
        provider.contribute(new ContextRequest.MentorChatRequest(1L, 2L, UUID.randomUUID()), files);

        JsonNode root = objectMapper.readTree(files.get("inputs/context/findings_history.json"));
        assertThat(root.get("summary").get("byPresence").get("PRESENT").asLong()).isEqualTo(3L);
        assertThat(root.get("summary").get("byPresence").get("ABSENT").asLong()).isEqualTo(1L);
        assertThat(root.get("summary").get("byPresence").get("NOT_APPLICABLE").asLong()).isEqualTo(0L);
        assertThat(root.get("summary").get("bySeverity").get("MAJOR").asLong()).isEqualTo(2L);
        assertThat(root.get("summary").get("totalObservations").asLong()).isEqualTo(4L);
    }

    @Test
    @DisplayName("firewall: raw rubric-voiced reasoning is scrubbed before it reaches the mentor (gap #1)")
    void rubricVoicedReasoningIsScrubbed() throws Exception {
        User user = new User();
        user.setLogin("octo");
        when(userRepository.findById(eq(2L))).thenReturn(Optional.of(user));

        var practice = new de.tum.cit.aet.hephaestus.practices.model.Practice();
        practice.setSlug("robust-error-handling");
        // A real student-facing sentence followed by a pure grading-mechanics sentence the detector echoed.
        String reasoning =
            "The retry block swallows the IOException without logging it. The assessment is BAD, capped at MINOR.";
        var observation = de.tum.cit.aet.hephaestus.practices.model.Observation.builder()
            .id(UUID.randomUUID())
            .title("Swallowed IOException")
            .practice(practice)
            .presence(Presence.PRESENT)
            .assessment(de.tum.cit.aet.hephaestus.practices.model.Assessment.BAD)
            .severity(Severity.MINOR)
            .confidence(0.9f)
            .observedAt(Instant.now())
            .reasoning(reasoning)
            .build();

        when(
            findingRepository.findRecentByDeveloperAndWorkspace(eq(2L), eq(1L), any(Instant.class), any(Pageable.class))
        ).thenReturn(List.of(observation));
        when(findingRepository.countByPresenceForDeveloper(eq(2L), eq(1L), any(Instant.class))).thenReturn(List.of());
        when(findingRepository.countBySeverityForDeveloper(eq(2L), eq(1L), any(Instant.class))).thenReturn(List.of());
        when(
            queryRepository.findReviewsReceivedSince(eq(1L), eq(2L), any(Instant.class), any(Pageable.class))
        ).thenReturn(List.of());

        Map<String, byte[]> files = new HashMap<>();
        provider.contribute(new ContextRequest.MentorChatRequest(1L, 2L, UUID.randomUUID()), files);

        JsonNode root = objectMapper.readTree(files.get("inputs/context/findings_history.json"));
        String shipped = root.get("recentObservations").get(0).get("reasoning").asString();
        // The student-facing sentence survives; the rubric mechanics ("assessment is BAD", "capped at MINOR")
        // do NOT reach the mentor.
        assertThat(shipped).contains("swallows the IOException");
        assertThat(shipped).doesNotContain("assessment is BAD");
        assertThat(shipped).doesNotContain("capped at MINOR");
    }

    @Test
    @DisplayName("rows: (presence,assessment) matrix nulls + populated reviewsReceived row")
    void recentObservationsAndReviewsPopulated() throws Exception {
        User user = new User();
        user.setLogin("octo");
        when(userRepository.findById(eq(2L))).thenReturn(Optional.of(user));

        var practiceBad = new de.tum.cit.aet.hephaestus.practices.model.Practice();
        practiceBad.setSlug("robust-error-handling");
        Instant observedBad = Instant.parse("2025-06-10T08:00:00Z");
        var badObservation = de.tum.cit.aet.hephaestus.practices.model.Observation.builder()
            .id(UUID.randomUUID())
            .title("Swallowed IOException")
            .practice(practiceBad)
            .presence(Presence.PRESENT)
            .assessment(de.tum.cit.aet.hephaestus.practices.model.Assessment.BAD)
            .severity(Severity.MAJOR)
            .confidence(0.9f)
            .observedAt(observedBad)
            .reasoning("The retry block swallows the IOException.")
            .build();

        var practiceNa = new de.tum.cit.aet.hephaestus.practices.model.Practice();
        practiceNa.setSlug("writes-tests");
        Instant observedNa = Instant.parse("2025-06-09T08:00:00Z");
        // NOT_APPLICABLE: assessment AND severity are null — must serialise as JSON null, not the enum name.
        var naObservation = de.tum.cit.aet.hephaestus.practices.model.Observation.builder()
            .id(UUID.randomUUID())
            .title("No test surface")
            .practice(practiceNa)
            .presence(Presence.NOT_APPLICABLE)
            .assessment(null)
            .severity(null)
            .confidence(0.5f)
            .observedAt(observedNa)
            .reasoning("Docs-only change.")
            .build();

        when(
            findingRepository.findRecentByDeveloperAndWorkspace(eq(2L), eq(1L), any(Instant.class), any(Pageable.class))
        ).thenReturn(List.of(badObservation, naObservation));
        when(findingRepository.countByPresenceForDeveloper(eq(2L), eq(1L), any(Instant.class))).thenReturn(List.of());
        when(findingRepository.countBySeverityForDeveloper(eq(2L), eq(1L), any(Instant.class))).thenReturn(List.of());

        var pr = new de.tum.cit.aet.hephaestus.integration.scm.domain.pullrequest.PullRequest();
        pr.setNumber(42);
        pr.setTitle("Add retry");
        var review = new de.tum.cit.aet.hephaestus.integration.scm.domain.pullrequestreview.PullRequestReview();
        review.setPullRequest(pr);
        var reviewer = new User();
        reviewer.setLogin("mentor-bot");
        review.setAuthor(reviewer);
        review.setState(
            de.tum.cit.aet.hephaestus.integration.scm.domain.pullrequestreview.PullRequestReview.State.CHANGES_REQUESTED
        );
        review.setBody("Please add a test.");
        review.setHtmlUrl("https://example.test/pr/42#review");
        review.setSubmittedAt(Instant.parse("2025-06-11T12:00:00Z"));
        when(
            queryRepository.findReviewsReceivedSince(eq(1L), eq(2L), any(Instant.class), any(Pageable.class))
        ).thenReturn(List.of(review));

        Map<String, byte[]> files = new HashMap<>();
        provider.contribute(new ContextRequest.MentorChatRequest(1L, 2L, UUID.randomUUID()), files);

        JsonNode root = objectMapper.readTree(files.get("inputs/context/findings_history.json"));

        JsonNode obs = root.get("recentObservations");
        assertThat(obs).hasSize(2);
        JsonNode bad = obs.get(0);
        assertThat(bad.get("practiceSlug").asString()).isEqualTo("robust-error-handling");
        assertThat(bad.get("title").asString()).isEqualTo("Swallowed IOException");
        assertThat(bad.get("presence").asString()).isEqualTo("PRESENT");
        assertThat(bad.get("assessment").asString()).isEqualTo("BAD");
        assertThat(bad.get("severity").asString()).isEqualTo("MAJOR");
        assertThat(bad.get("observedAt").asString()).isEqualTo(observedBad.toString());

        JsonNode na = obs.get(1);
        assertThat(na.get("presence").asString()).isEqualTo("NOT_APPLICABLE");
        // assessment/severity must be JSON null (not the string "null", not absent).
        assertThat(na.get("assessment").isNull()).isTrue();
        assertThat(na.get("severity").isNull()).isTrue();

        JsonNode reviews = root.get("reviewsReceived");
        assertThat(reviews).hasSize(1);
        JsonNode r0 = reviews.get(0);
        assertThat(r0.get("prNumber").asInt()).isEqualTo(42);
        assertThat(r0.get("prTitle").asString()).isEqualTo("Add retry");
        assertThat(r0.get("reviewer").asString()).isEqualTo("mentor-bot");
        assertThat(r0.get("state").asString()).isEqualTo("CHANGES_REQUESTED");
        assertThat(r0.get("hasComment").asBoolean()).isTrue();
        assertThat(r0.get("submittedAt").asString()).isEqualTo("2025-06-11T12:00:00Z");
    }

    private static PresenceCount mockObservationCount(Presence v, long c) {
        return new PresenceCount() {
            @Override
            public Presence getPresence() {
                return v;
            }

            @Override
            public Long getCount() {
                return c;
            }
        };
    }

    private static SeverityCount mockSeverityCount(Severity s, long c) {
        return new SeverityCount() {
            @Override
            public Severity getSeverity() {
                return s;
            }

            @Override
            public Long getCount() {
                return c;
            }
        };
    }
}
