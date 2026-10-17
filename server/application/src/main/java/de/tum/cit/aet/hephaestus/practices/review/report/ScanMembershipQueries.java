package de.tum.cit.aet.hephaestus.practices.review.report;

import de.tum.cit.aet.hephaestus.practices.review.model.Review;
import de.tum.cit.aet.hephaestus.practices.spi.ProblemBaselineState;
import de.tum.cit.aet.hephaestus.practices.spi.ResultOutcome;
import de.tum.cit.aet.hephaestus.practices.spi.Severity;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

public interface ScanMembershipQueries {
    List<ItemKey> items(Review review, @Nullable ScanReportCursor after, int size, ItemFilter filter);

    List<ProblemKey> problems(Review review, @Nullable ScanReportCursor after, int size, ProblemFilter filter);

    List<UUID> proofIds(
            long workspaceId,
            List<UUID> reviewIds,
            @Nullable UUID after,
            int limit,
            @Nullable Long practiceId,
            boolean openOnly);

    List<ScanReportCursor> comparisonPairs(
            Review review, @Nullable UUID baseline, @Nullable ScanReportCursor after, int limit);

    record ItemKey(ScanReportCursor cursor, @Nullable UUID resultId, int problemCount) {}

    record ProblemKey(
            ScanReportCursor cursor,
            UUID resultId,
            @Nullable ProblemBaselineState state,
            @Nullable UUID previousProblemId) {}

    record ItemFilter(
            @Nullable String practiceSlug,
            @Nullable ResultOutcome outcome,
            @Nullable String status,
            @Nullable Boolean reused,
            @Nullable String path) {}

    record ProblemFilter(
            @Nullable String practiceSlug,
            @Nullable Severity severity,
            @Nullable String path,
            @Nullable ProblemBaselineState baselineState,
            boolean openOnly) {}
}
