package de.tum.cit.aet.hephaestus.integration.core.spi;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

public record ScanExportProblem(
        UUID problemId,
        UUID resultId,
        long itemId,
        String itemVersion,
        long practiceVersionId,
        String practiceSlug,
        String practiceName,
        String criteria,
        String reviewDigest,
        String severity,
        String summary,
        String path,
        String stableIdentity,
        @Nullable String baselineState,
        boolean absenceProved,
        List<ScanExportLocation> citations) {
    public ScanExportProblem {
        if (itemId <= 0
                || practiceVersionId <= 0
                || practiceSlug.isBlank()
                || stableIdentity.isBlank()
                || !Set.of("CRITICAL", "MAJOR", "MINOR", "TRIVIAL").contains(severity)
                || (baselineState != null
                        && !Set.of("NEW", "UNCHANGED", "UPDATED", "ABSENT").contains(baselineState))
                || ("ABSENT".equals(baselineState) && !absenceProved))
            throw new IllegalArgumentException("Invalid scan problem export facts");
        new ScanExportLocation(path, null, null, null);
        citations = List.copyOf(citations);
    }
}
