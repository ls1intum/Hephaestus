package de.tum.cit.aet.hephaestus.practices.spi;

import de.tum.cit.aet.hephaestus.core.privacy.spi.PersonCopyIdentity;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemRef;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;

public record ResultHistoryEntry(
        UUID resultId,
        UUID reviewId,
        long practiceId,
        String practiceSlug,
        int practiceVersion,
        long itemId,
        String itemKind,
        String itemVersion,
        @Nullable Long developerAccountId,
        ResultOutcome outcome,
        @Nullable Severity severity,
        @Nullable String summary,
        Instant recordedAt,
        @Nullable Instant endedAt,
        @Nullable ResultEndReason endReason,
        List<ResultMarkKind> activeMarks,
        List<ProblemHistoryEntry> problems,
        List<String> openQuestions,
        ItemRef item,
        ReviewOrigin origin,
        @Nullable String explanation,
        @Nullable JsonNode evidence,
        @Nullable JsonNode example,
        @Nullable JsonNode coverage,
        @Nullable JsonNode criteria,
        short evidenceVersion,
        List<UUID> sourceJobIds,
        List<PersonCopyIdentity> copiedIdentities,
        List<Long> repositoryIds,
        boolean copyAuthorityComplete) {
    public ResultHistoryEntry {
        activeMarks = List.copyOf(activeMarks);
        problems = List.copyOf(problems);
        openQuestions = List.copyOf(openQuestions);
        sourceJobIds = List.copyOf(sourceJobIds);
        copiedIdentities = List.copyOf(copiedIdentities);
        repositoryIds = List.copyOf(repositoryIds);
        evidence = evidence == null ? null : evidence.deepCopy();
        example = example == null ? null : example.deepCopy();
        coverage = coverage == null ? null : coverage.deepCopy();
        criteria = criteria == null ? null : criteria.deepCopy();
    }

    @Override
    public @Nullable JsonNode evidence() {
        return evidence == null ? null : evidence.deepCopy();
    }

    @Override
    public @Nullable JsonNode example() {
        return example == null ? null : example.deepCopy();
    }

    @Override
    public @Nullable JsonNode coverage() {
        return coverage == null ? null : coverage.deepCopy();
    }

    @Override
    public @Nullable JsonNode criteria() {
        return criteria == null ? null : criteria.deepCopy();
    }
}
