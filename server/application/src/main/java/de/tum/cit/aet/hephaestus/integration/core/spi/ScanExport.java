package de.tum.cit.aet.hephaestus.integration.core.spi;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

public record ScanExport(
        UUID scanId,
        long workspaceId,
        long repositoryItemId,
        String repositoryUrl,
        String commit,
        String selectorDigest,
        String modelDigest,
        int epoch,
        boolean active,
        boolean expired,
        boolean complete,
        boolean listingComplete,
        @Nullable UUID baselineScanId,
        boolean baselineComparable,
        Map<String, Long> counts,
        List<String> comparisonReasons,
        List<ScanExportProblem> problems) {
    public ScanExport {
        if (workspaceId <= 0 || repositoryItemId <= 0 || epoch < 0 || !commit.matches("[0-9a-f]{40}|[0-9a-f]{64}"))
            throw new IllegalArgumentException("Invalid scan export identity");
        counts = Map.copyOf(counts);
        comparisonReasons = List.copyOf(comparisonReasons);
        problems = List.copyOf(problems);
    }
}
