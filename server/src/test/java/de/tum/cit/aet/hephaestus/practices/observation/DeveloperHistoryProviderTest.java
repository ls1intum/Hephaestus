package de.tum.cit.aet.hephaestus.practices.observation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.practices.model.Assessment;
import de.tum.cit.aet.hephaestus.practices.model.Presence;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class DeveloperHistoryProviderTest extends BaseUnitTest {

    private static final Long CONTRIBUTOR_ID = 42L;
    private static final Long WORKSPACE_ID = 99L;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private ObservationRepository observationRepository;

    private DeveloperHistoryProvider provider;

    @BeforeEach
    void setUp() {
        provider = new DeveloperHistoryProvider(observationRepository, objectMapper);
    }

    @Nested
    class BuildHistoryJson {

        @Test
        void returnsEmptyForNoFindings() {
            when(observationRepository.findDeveloperPracticeSummary(CONTRIBUTOR_ID, WORKSPACE_ID)).thenReturn(
                List.of()
            );

            Optional<byte[]> result = provider.buildHistoryJson(CONTRIBUTOR_ID, WORKSPACE_ID);

            assertThat(result).isEmpty();
        }

        @Test
        void buildsSinglePracticeJson() throws Exception {
            when(observationRepository.findDeveloperPracticeSummary(CONTRIBUTOR_ID, WORKSPACE_ID)).thenReturn(
                List.of(
                    summary("pr-description-quality", Presence.ABSENT, 3, Instant.parse("2026-03-20T14:30:00Z")),
                    summary("pr-description-quality", Presence.PRESENT, 1, Instant.parse("2026-03-18T10:00:00Z"))
                )
            );

            byte[] json = provider.buildHistoryJson(CONTRIBUTOR_ID, WORKSPACE_ID).orElseThrow();
            JsonNode root = objectMapper.readTree(json);

            assertThat(root.isArray()).isTrue();
            assertThat(root).hasSize(1);

            JsonNode entry = root.get(0);
            assertThat(entry.get("practice").asString()).isEqualTo("pr-description-quality");
            assertThat(entry.get("good").asLong()).isEqualTo(1);
            assertThat(entry.get("bad").asLong()).isEqualTo(3);
            assertThat(entry.get("lastSeen").asString()).isEqualTo("2026-03-20T14:30:00Z");
        }

        @Test
        void aggregatesMultiplePractices() throws Exception {
            when(observationRepository.findDeveloperPracticeSummary(CONTRIBUTOR_ID, WORKSPACE_ID)).thenReturn(
                List.of(
                    summary("error-handling", Presence.ABSENT, 2, Instant.parse("2026-03-18T10:15:00Z")),
                    summary("error-handling", Presence.ABSENT, 1, Instant.parse("2026-03-19T12:00:00Z")),
                    summary("pr-description-quality", Presence.PRESENT, 5, Instant.parse("2026-03-20T14:30:00Z")),
                    summary("pr-description-quality", Presence.ABSENT, 1, Instant.parse("2026-03-15T08:00:00Z"))
                )
            );

            byte[] json = provider.buildHistoryJson(CONTRIBUTOR_ID, WORKSPACE_ID).orElseThrow();
            JsonNode root = objectMapper.readTree(json);

            assertThat(root).hasSize(2);

            // Sorted by NEGATIVE desc: error-handling (3) before pr-description-quality (1)
            JsonNode first = root.get(0);
            assertThat(first.get("practice").asString()).isEqualTo("error-handling");
            assertThat(first.get("bad").asLong()).isEqualTo(3);
            assertThat(first.get("lastSeen").asString()).isEqualTo("2026-03-19T12:00:00Z");

            JsonNode second = root.get(1);
            assertThat(second.get("practice").asString()).isEqualTo("pr-description-quality");
            assertThat(second.get("good").asLong()).isEqualTo(5);
            assertThat(second.get("bad").asLong()).isEqualTo(1);
        }

        @Test
        void capsAtMaxPractices() throws Exception {
            List<DeveloperPracticeSummary> summaries = new ArrayList<>();
            // Create 25 practices (exceeds MAX_PRACTICES=20)
            for (int i = 0; i < 25; i++) {
                String slug = String.format("practice-%02d", i);
                summaries.add(summary(slug, Presence.ABSENT, i, Instant.parse("2026-03-20T10:00:00Z")));
            }
            when(observationRepository.findDeveloperPracticeSummary(CONTRIBUTOR_ID, WORKSPACE_ID)).thenReturn(
                summaries
            );

            byte[] json = provider.buildHistoryJson(CONTRIBUTOR_ID, WORKSPACE_ID).orElseThrow();
            JsonNode root = objectMapper.readTree(json);

            assertThat(root).hasSize(DeveloperHistoryProvider.MAX_PRACTICES);

            // First entry should be the one with most negatives (practice-24)
            assertThat(root.get(0).get("practice").asString()).isEqualTo("practice-24");
            assertThat(root.get(0).get("bad").asLong()).isEqualTo(24);

            // Last entry should be practice-05 (index 19 in reversed order: 24,23,...,5)
            assertThat(root.get(19).get("practice").asString()).isEqualTo("practice-05");
        }

        @Test
        void alphabeticalTiebreaker() throws Exception {
            when(observationRepository.findDeveloperPracticeSummary(CONTRIBUTOR_ID, WORKSPACE_ID)).thenReturn(
                List.of(
                    summary("zebra-practice", Presence.ABSENT, 2, Instant.parse("2026-03-20T10:00:00Z")),
                    summary("alpha-practice", Presence.ABSENT, 2, Instant.parse("2026-03-20T10:00:00Z"))
                )
            );

            byte[] json = provider.buildHistoryJson(CONTRIBUTOR_ID, WORKSPACE_ID).orElseThrow();
            JsonNode root = objectMapper.readTree(json);

            assertThat(root.get(0).get("practice").asString()).isEqualTo("alpha-practice");
            assertThat(root.get(1).get("practice").asString()).isEqualTo("zebra-practice");
        }

        @Test
        void lastSeenReflectsMaxAcrossObservations() throws Exception {
            when(observationRepository.findDeveloperPracticeSummary(CONTRIBUTOR_ID, WORKSPACE_ID)).thenReturn(
                List.of(
                    summary("commit-quality", Presence.PRESENT, 1, Instant.parse("2026-03-15T10:00:00Z")),
                    summary("commit-quality", Presence.ABSENT, 1, Instant.parse("2026-03-20T14:00:00Z"))
                )
            );

            byte[] json = provider.buildHistoryJson(CONTRIBUTOR_ID, WORKSPACE_ID).orElseThrow();
            JsonNode root = objectMapper.readTree(json);

            assertThat(root.get(0).get("lastSeen").asString()).isEqualTo("2026-03-20T14:00:00Z");
        }

        @Test
        @DisplayName("returns empty when ObjectMapper throws JacksonException")
        void returnsEmptyOnSerializationFailure() throws Exception {
            ObjectMapper brokenMapper = org.mockito.Mockito.mock(ObjectMapper.class);
            when(brokenMapper.createArrayNode()).thenReturn(objectMapper.createArrayNode());
            when(brokenMapper.createObjectNode()).thenReturn(objectMapper.createObjectNode());
            when(brokenMapper.writeValueAsBytes(any())).thenThrow(
                new JacksonException("Simulated serialization failure") {}
            );

            DeveloperHistoryProvider brokenProvider = new DeveloperHistoryProvider(
                observationRepository,
                brokenMapper
            );

            when(observationRepository.findDeveloperPracticeSummary(CONTRIBUTOR_ID, WORKSPACE_ID)).thenReturn(
                List.of(summary("test-practice", Presence.ABSENT, 1, Instant.parse("2026-03-20T10:00:00Z")))
            );

            Optional<byte[]> result = brokenProvider.buildHistoryJson(CONTRIBUTOR_ID, WORKSPACE_ID);

            assertThat(result).isEmpty();
        }
    }

    /**
     * Creates a mock {@link DeveloperPracticeSummary} for testing.
     */
    private static DeveloperPracticeSummary summary(
        String practiceSlug,
        Presence presence,
        long count,
        Instant lastDetectedAt
    ) {
        // Former-GOOD practices: PRESENT -> GOOD (strength), ABSENT -> BAD (problem), NA -> null.
        Assessment assessment = switch (presence) {
            case PRESENT -> Assessment.GOOD;
            case ABSENT -> Assessment.BAD;
            case NOT_APPLICABLE -> null;
        };
        return new DeveloperPracticeSummary() {
            @Override
            public String getPracticeSlug() {
                return practiceSlug;
            }

            @Override
            public Presence getPresence() {
                return presence;
            }

            @Override
            public Assessment getAssessment() {
                return assessment;
            }

            @Override
            public long getCount() {
                return count;
            }

            @Override
            public Instant getLastDetectedAt() {
                return lastDetectedAt;
            }
        };
    }
}
