package de.tum.cit.aet.hephaestus.practices.observation;

import de.tum.cit.aet.hephaestus.practices.model.Assessment;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Builds aggregated developer practice history JSON for the review agent context.
 *
 * <p>Queries {@link ObservationRepository} for observation counts per practice and
 * produces a compact JSON array suitable for injection into the agent sandbox as
 * {@code inputs/context/contributor_history.json}.
 *
 * <p>Output is capped at {@value #MAX_PRACTICES} practices, sorted by BAD count
 * descending so the most problematic practices are always included when truncation occurs.
 */
@Component
public class DeveloperHistoryProvider {

    private static final Logger log = LoggerFactory.getLogger(DeveloperHistoryProvider.class);

    /** Maximum number of practices included in the history JSON. */
    static final int MAX_PRACTICES = 20;

    private final ObservationRepository observationRepository;
    private final ObjectMapper objectMapper;

    public DeveloperHistoryProvider(ObservationRepository observationRepository, ObjectMapper objectMapper) {
        this.observationRepository = observationRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Builds developer practice history JSON for agent context injection.
     *
     * @param developerId the developer whose history to aggregate
     * @param workspaceId   the workspace scope
     * @return compact JSON bytes, or empty if the developer has no relevant history
     */
    public Optional<byte[]> buildHistoryJson(Long developerId, Long workspaceId) {
        List<DeveloperPracticeSummary> summaries = observationRepository.findDeveloperPracticeSummary(
            developerId,
            workspaceId
        );

        if (summaries.isEmpty()) {
            return Optional.empty();
        }

        // Group by practice slug, accumulate observation counts and track latest observation
        Map<String, PracticeAggregate> byPractice = new LinkedHashMap<>();
        for (DeveloperPracticeSummary row : summaries) {
            byPractice.computeIfAbsent(row.getPracticeSlug(), slug -> new PracticeAggregate()).add(row);
        }

        // Sort by problem (BAD) count desc, then slug for deterministic ordering
        List<Map.Entry<String, PracticeAggregate>> sorted = byPractice
            .entrySet()
            .stream()
            .sorted(
                Comparator.<Map.Entry<String, PracticeAggregate>>comparingLong(e -> e.getValue().bad)
                    .reversed()
                    .thenComparing(Map.Entry::getKey)
            )
            .limit(MAX_PRACTICES)
            .toList();

        ArrayNode array = objectMapper.createArrayNode();
        for (Map.Entry<String, PracticeAggregate> entry : sorted) {
            ObjectNode node = objectMapper.createObjectNode();
            PracticeAggregate agg = entry.getValue();
            node.put("practice", entry.getKey());
            node.put("good", agg.good);
            node.put("bad", agg.bad);
            node.put("lastSeen", agg.lastObservedAt.toString());
            array.add(node);
        }

        try {
            byte[] json = objectMapper.writeValueAsBytes(array);
            log.debug(
                "Built developer history: {} practices, {} bytes, developerId={}, workspaceId={}",
                sorted.size(),
                json.length,
                developerId,
                workspaceId
            );
            return Optional.of(json);
        } catch (JacksonException e) {
            // Should never happen for ObjectNode serialization
            log.error("Failed to serialize developer history JSON", e);
            return Optional.empty();
        }
    }

    /**
     * Mutable accumulator for per-practice assessment counts during aggregation (ADR 0022): {@code good} =
     * strengths ({@code assessment=GOOD}), {@code bad} = problems ({@code assessment=BAD}). NOT_APPLICABLE
     * rows carry a null assessment and are not counted.
     */
    private static final class PracticeAggregate {

        long good;
        long bad;
        Instant lastObservedAt;

        void add(DeveloperPracticeSummary row) {
            Assessment assessment = row.getAssessment();
            if (assessment == Assessment.GOOD) {
                good += row.getCount();
            } else if (assessment == Assessment.BAD) {
                bad += row.getCount();
            }
            // NOT_APPLICABLE (null assessment) is not counted in history.
            if (lastObservedAt == null || row.getLastObservedAt().isAfter(lastObservedAt)) {
                lastObservedAt = row.getLastObservedAt();
            }
        }
    }
}
