package de.tum.cit.aet.hephaestus.practices.review.report;

import de.tum.cit.aet.hephaestus.practices.spi.ProblemBaselineState;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

public record ScanProblemComparison(
        UUID problemId,
        ProblemBaselineState state,
        @Nullable UUID previousProblemId) {}
