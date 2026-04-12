package de.tum.in.www1.hephaestus.practices.finding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.tum.in.www1.hephaestus.practices.model.Verdict;
import de.tum.in.www1.hephaestus.testconfig.BaseUnitTest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

@DisplayName("ContributorHistoryProvider")
class ContributorHistoryProviderTest extends BaseUnitTest {

    private static final Long CONTRIBUTOR_ID = 42L;
    private static final Long WORKSPACE_ID = 99L;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private PracticeFindingRepository practiceFindingRepository;

    private ContributorHistoryProvider provider;

    @BeforeEach
    void setUp() {
        provider = new ContributorHistoryProvider(practiceFindingRepository, objectMapper);
    }

    @Nested
    @DisplayName("buildHistoryJson")
    class BuildHistoryJson {

        @Test
        @DisplayName("returns empty when no findings exist")
        void returnsEmptyForNoFindings() {
            when(practiceFindingRepository.findContributorPracticeSummary(CONTRIBUTOR_ID, WORKSPACE_ID)).thenReturn(
                List.of()
            );

            Optional<byte[]> result = provider.buildHistoryJson(CONTRIBUTOR_ID, WORKSPACE_ID);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("builds correct JSON structure for single practice")
        void buildsSinglePracticeJson() throws Exception {
            when(practiceFindingRepository.findContributorPracticeSummary(CONTRIBUTOR_ID, WORKSPACE_ID)).thenReturn(
                List.of(
                    summary("pr-description-quality", Verdict.NEGATIVE, 3, Instant.parse("2026-03-20T14:30:00Z")),
                    summary("pr-description-quality", Verdict.POSITIVE, 1, Instant.parse("2026-03-18T10:00:00Z"))
                )
            );

            byte[] json = provider.buildHistoryJson(CONTRIBUTOR_ID, WORKSPACE_ID).orElseThrow();
            JsonNode root = objectMapper.readTree(json);

            assertThat(root.isArray()).isTrue();
            assertThat(root).hasSize(1);

            JsonNode entry = root.get(0);
            assertThat(entry.get("practice").asText()).isEqualTo("pr-description-quality");
            assertThat(entry.get("positive").asLong()).isEqualTo(1);
            assertThat(entry.get("negative").asLong()).isEqualTo(3);
            assertThat(entry.get("lastSeen").asText()).isEqualTo("2026-03-20T14:30:00Z");
        }

        @Test
        @DisplayName("aggregates multiple practices with mixed verdicts")
        void aggregatesMultiplePractices() throws Exception {
            when(practiceFindingRepository.findContributorPracticeSummary(CONTRIBUTOR_ID, WORKSPACE_ID)).thenReturn(
                List.of(
                    summary("error-handling", Verdict.NEGATIVE, 2, Instant.parse("2026-03-18T10:15:00Z")),
                    summary("error-handling", Verdict.NEGATIVE, 1, Instant.parse("2026-03-19T12:00:00Z")),
                    summary("pr-description-quality", Verdict.POSITIVE, 5, Instant.parse("2026-03-20T14:30:00Z")),
                    summary("pr-description-quality", Verdict.NEGATIVE, 1, Instant.parse("2026-03-15T08:00:00Z"))
                )
            );

            byte[] json = provider.buildHistoryJson(CONTRIBUTOR_ID, WORKSPACE_ID).orElseThrow();
            JsonNode root = objectMapper.readTree(json);

            assertThat(root).hasSize(2);

            // Sorted by NEGATIVE desc: error-handling (3) before pr-description-quality (1)
            JsonNode first = root.get(0);
            assertThat(first.get("practice").asText()).isEqualTo("error-handling");
            assertThat(first.get("negative").asLong()).isEqualTo(3);
            assertThat(first.get("lastSeen").asText()).isEqualTo("2026-03-19T12:00:00Z");

            JsonNode second = root.get(1);
            assertThat(second.get("practice").asText()).isEqualTo("pr-description-quality");
            assertThat(second.get("positive").asLong()).isEqualTo(5);
            assertThat(second.get("negative").asLong()).isEqualTo(1);
        }

        @Test
        @DisplayName("caps output at MAX_PRACTICES sorted by NEGATIVE count")
        void capsAtMaxPractices() throws Exception {
            List<ContributorPracticeSummary> summaries = new ArrayList<>();
            // Create 25 practices (exceeds MAX_PRACTICES=20)
            for (int i = 0; i < 25; i++) {
                String slug = String.format("practice-%02d", i);
                summaries.add(summary(slug, Verdict.NEGATIVE, i, Instant.parse("2026-03-20T10:00:00Z")));
            }
            when(practiceFindingRepository.findContributorPracticeSummary(CONTRIBUTOR_ID, WORKSPACE_ID)).thenReturn(
                summaries
            );

            byte[] json = provider.buildHistoryJson(CONTRIBUTOR_ID, WORKSPACE_ID).orElseThrow();
            JsonNode root = objectMapper.readTree(json);

            assertThat(root).hasSize(ContributorHistoryProvider.MAX_PRACTICES);

            // First entry should be the one with most negatives (practice-24)
            assertThat(root.get(0).get("practice").asText()).isEqualTo("practice-24");
            assertThat(root.get(0).get("negative").asLong()).isEqualTo(24);

            // Last entry should be practice-05 (index 19 in reversed order: 24,23,...,5)
            assertThat(root.get(19).get("practice").asText()).isEqualTo("practice-05");
        }

        @Test
        @DisplayName("uses alphabetical order as tiebreaker when NEGATIVE counts are equal")
        void alphabeticalTiebreaker() throws Exception {
            when(practiceFindingRepository.findContributorPracticeSummary(CONTRIBUTOR_ID, WORKSPACE_ID)).thenReturn(
                List.of(
                    summary("zebra-practice", Verdict.NEGATIVE, 2, Instant.parse("2026-03-20T10:00:00Z")),
                    summary("alpha-practice", Verdict.NEGATIVE, 2, Instant.parse("2026-03-20T10:00:00Z"))
                )
            );

            byte[] json = provider.buildHistoryJson(CONTRIBUTOR_ID, WORKSPACE_ID).orElseThrow();
            JsonNode root = objectMapper.readTree(json);

            assertThat(root.get(0).get("practice").asText()).isEqualTo("alpha-practice");
            assertThat(root.get(1).get("practice").asText()).isEqualTo("zebra-practice");
        }

        @Test
        @DisplayName("lastSeen reflects maximum across all verdicts for a practice")
        void lastSeenReflectsMaxAcrossVerdicts() throws Exception {
            when(practiceFindingRepository.findContributorPracticeSummary(CONTRIBUTOR_ID, WORKSPACE_ID)).thenReturn(
                List.of(
                    summary("commit-quality", Verdict.POSITIVE, 1, Instant.parse("2026-03-15T10:00:00Z")),
                    summary("commit-quality", Verdict.NEGATIVE, 1, Instant.parse("2026-03-20T14:00:00Z"))
                )
            );

            byte[] json = provider.buildHistoryJson(CONTRIBUTOR_ID, WORKSPACE_ID).orElseThrow();
            JsonNode root = objectMapper.readTree(json);

            assertThat(root.get(0).get("lastSeen").asText()).isEqualTo("2026-03-20T14:00:00Z");
        }

        @Test
        @DisplayName("returns empty when ObjectMapper throws JsonProcessingException")
        void returnsEmptyOnSerializationFailure() throws Exception {
            ObjectMapper brokenMapper = org.mockito.Mockito.mock(ObjectMapper.class);
            when(brokenMapper.createArrayNode()).thenReturn(objectMapper.createArrayNode());
            when(brokenMapper.createObjectNode()).thenReturn(objectMapper.createObjectNode());
            when(brokenMapper.writeValueAsBytes(any())).thenThrow(
                new JsonProcessingException("Simulated serialization failure") {}
            );

            ContributorHistoryProvider brokenProvider = new ContributorHistoryProvider(
                practiceFindingRepository,
                brokenMapper
            );

            when(practiceFindingRepository.findContributorPracticeSummary(CONTRIBUTOR_ID, WORKSPACE_ID)).thenReturn(
                List.of(summary("test-practice", Verdict.NEGATIVE, 1, Instant.parse("2026-03-20T10:00:00Z")))
            );

            Optional<byte[]> result = brokenProvider.buildHistoryJson(CONTRIBUTOR_ID, WORKSPACE_ID);

            assertThat(result).isEmpty();
        }
    }

    /**
     * Creates a mock {@link ContributorPracticeSummary} for testing.
     */
    private static ContributorPracticeSummary summary(
        String practiceSlug,
        Verdict verdict,
        long count,
        Instant lastDetectedAt
    ) {
        return new ContributorPracticeSummary() {
            @Override
            public String getPracticeSlug() {
                return practiceSlug;
            }

            @Override
            public Verdict getVerdict() {
                return verdict;
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
