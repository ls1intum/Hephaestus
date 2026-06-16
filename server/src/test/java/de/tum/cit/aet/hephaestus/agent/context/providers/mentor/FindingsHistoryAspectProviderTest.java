package de.tum.cit.aet.hephaestus.agent.context.providers.mentor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.agent.context.ContextRequest;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.User;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.UserRepository;
import de.tum.cit.aet.hephaestus.practices.finding.PracticeFindingRepository;
import de.tum.cit.aet.hephaestus.practices.finding.PracticeFindingRepository.SeverityCount;
import de.tum.cit.aet.hephaestus.practices.finding.PracticeFindingRepository.VerdictCount;
import de.tum.cit.aet.hephaestus.practices.model.Observation;
import de.tum.cit.aet.hephaestus.practices.model.Severity;
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

class FindingsHistoryAspectProviderTest extends BaseUnitTest {

    @Mock
    UserRepository userRepository;

    @Mock
    PracticeFindingRepository findingRepository;

    @Mock
    MentorAspectQueryRepository queryRepository;

    @Spy
    ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    CacheManager cacheManager;

    @InjectMocks
    FindingsHistoryAspectProvider provider;

    @Test
    void emptyDefaults() throws Exception {
        User user = new User();
        user.setLogin("octo");
        when(userRepository.findById(eq(2L))).thenReturn(Optional.of(user));
        when(
            findingRepository.findRecentByDeveloperAndWorkspace(eq(2L), eq(1L), any(Instant.class), any(Pageable.class))
        ).thenReturn(List.of());
        when(findingRepository.countByVerdictForDeveloper(eq(2L), eq(1L), any(Instant.class))).thenReturn(List.of());
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
        assertThat(root.get("summary").get("totalFindings").asLong()).isEqualTo(0L);
        // All verdicts present even when count is 0 — keeps the wire shape stable.
        for (Observation v : Observation.values()) {
            assertThat(root.get("summary").get("byVerdict").has(v.name())).isTrue();
        }
        for (Severity s : Severity.values()) {
            assertThat(root.get("summary").get("bySeverity").has(s.name())).isTrue();
        }
        assertThat(root.get("recentFindings").isArray()).isTrue();
        assertThat(root.get("reviewsReceived").isArray()).isTrue();
    }

    @Test
    @DisplayName("aggregations: verdict + severity counts populate the summary")
    void aggregationsPopulated() throws Exception {
        User user = new User();
        user.setLogin("octo");
        when(userRepository.findById(eq(2L))).thenReturn(Optional.of(user));
        when(
            findingRepository.findRecentByDeveloperAndWorkspace(eq(2L), eq(1L), any(Instant.class), any(Pageable.class))
        ).thenReturn(List.of());
        VerdictCount positive = mockVerdictCount(Observation.OBSERVED, 3L);
        VerdictCount negative = mockVerdictCount(Observation.NOT_OBSERVED, 1L);
        when(findingRepository.countByVerdictForDeveloper(eq(2L), eq(1L), any(Instant.class))).thenReturn(
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
        assertThat(root.get("summary").get("byVerdict").get("OBSERVED").asLong()).isEqualTo(3L);
        assertThat(root.get("summary").get("byVerdict").get("NOT_OBSERVED").asLong()).isEqualTo(1L);
        assertThat(root.get("summary").get("byVerdict").get("NOT_APPLICABLE").asLong()).isEqualTo(0L);
        assertThat(root.get("summary").get("bySeverity").get("MAJOR").asLong()).isEqualTo(2L);
        assertThat(root.get("summary").get("totalFindings").asLong()).isEqualTo(4L);
    }

    private static VerdictCount mockVerdictCount(Observation v, long c) {
        return new VerdictCount() {
            @Override
            public Observation getVerdict() {
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
