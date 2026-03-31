package de.tum.in.www1.hephaestus.practices.finding;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import de.tum.in.www1.hephaestus.practices.model.Verdict;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Builds aggregated contributor practice history JSON for the review agent context.
 *
 * <p>Queries {@link PracticeFindingRepository} for verdict counts per practice and
 * produces a compact JSON array suitable for injection into the agent sandbox as
 * {@code .context/contributor_history.json}.
 *
 * <p>Output is capped at {@value #MAX_PRACTICES} practices, sorted by NEGATIVE count
 * descending so the most problematic practices are always included when truncation occurs.
 */
public class ContributorHistoryProvider {

    private static final Logger log = LoggerFactory.getLogger(ContributorHistoryProvider.class);

    /** Maximum number of practices included in the history JSON. */
    static final int MAX_PRACTICES = 20;

    private final PracticeFindingRepository practiceFindingRepository;
    private final ObjectMapper objectMapper;

    public ContributorHistoryProvider(PracticeFindingRepository practiceFindingRepository, ObjectMapper objectMapper) {
        this.practiceFindingRepository = practiceFindingRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Builds contributor practice history JSON for agent context injection.
     *
     * @param contributorId the contributor whose history to aggregate
     * @param workspaceId   the workspace scope
     * @return compact JSON bytes, or empty if the contributor has no relevant history
     */
    public Optional<byte[]> buildHistoryJson(Long contributorId, Long workspaceId) {
        List<ContributorPracticeSummary> summaries = practiceFindingRepository.findContributorPracticeSummary(
            contributorId,
            workspaceId
        );

        if (summaries.isEmpty()) {
            return Optional.empty();
        }

        // Group by practice slug, accumulate verdict counts and track latest detection
        Map<String, PracticeAggregate> byPractice = new LinkedHashMap<>();
        for (ContributorPracticeSummary row : summaries) {
            byPractice.computeIfAbsent(row.getPracticeSlug(), slug -> new PracticeAggregate()).add(row);
        }

        if (byPractice.isEmpty()) {
            return Optional.empty();
        }

        // Sort by NEGATIVE count desc, then slug for deterministic ordering
        List<Map.Entry<String, PracticeAggregate>> sorted = byPractice
            .entrySet()
            .stream()
            .sorted(
                Comparator.<Map.Entry<String, PracticeAggregate>>comparingLong(e -> e.getValue().negative)
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
            node.put("positive", agg.positive);
            node.put("negative", agg.negative);
            node.put("lastSeen", agg.lastDetectedAt.toString());
            array.add(node);
        }

        try {
            byte[] json = objectMapper.writeValueAsBytes(array);
            log.debug(
                "Built contributor history: {} practices, {} bytes, contributorId={}, workspaceId={}",
                sorted.size(),
                json.length,
                contributorId,
                workspaceId
            );
            return Optional.of(json);
        } catch (JsonProcessingException e) {
            // Should never happen for ObjectNode serialization
            log.error("Failed to serialize contributor history JSON", e);
            return Optional.empty();
        }
    }

    /**
     * Mutable accumulator for per-practice verdict counts during aggregation.
     */
    private static final class PracticeAggregate {

        long positive;
        long negative;
        Instant lastDetectedAt;

        void add(ContributorPracticeSummary row) {
            switch (row.getVerdict()) {
                case POSITIVE -> positive += row.getCount();
                case NEGATIVE -> negative += row.getCount();
            }
            if (lastDetectedAt == null || row.getLastDetectedAt().isAfter(lastDetectedAt)) {
                lastDetectedAt = row.getLastDetectedAt();
            }
        }
    }
}
