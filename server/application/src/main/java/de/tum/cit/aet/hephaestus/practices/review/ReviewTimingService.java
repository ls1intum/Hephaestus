package de.tum.cit.aet.hephaestus.practices.review;

import de.tum.cit.aet.hephaestus.core.runtime.ConditionalOnServerRole;
import de.tum.cit.aet.hephaestus.core.time.TimeRange;
import de.tum.cit.aet.hephaestus.practices.review.dto.ReviewTimeSharesDTO;
import de.tum.cit.aet.hephaestus.practices.review.dto.ReviewTimingDTO;
import de.tum.cit.aet.hephaestus.practices.review.dto.ReviewTimingStageDTO;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewRuntimeQuery;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewTimingStage;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewTimingSums;
import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnServerRole
@RequiredArgsConstructor
class ReviewTimingService {

    static final int LONG_WAIT_MINUTES = 10;
    private static final ReviewTimingStage EMPTY = new ReviewTimingStage(0, null, null);

    private final ReviewRuntimeQuery runtime;
    private final Clock clock;

    @Transactional(readOnly = true)
    public ReviewTimingDTO timing(long workspaceId, TimeRange range) {
        var snapshot = runtime.timing(
                workspaceId, range.from(), range.to(), clock.instant().minus(Duration.ofMinutes(LONG_WAIT_MINUTES)));
        var stages = snapshot.stages();
        return new ReviewTimingDTO(
                range.from(),
                range.to(),
                stage(stages, "ASKED"),
                stage(stages, "QUEUE"),
                stage(stages, "PREPARATION"),
                stage(stages, "MODEL"),
                stage(stages, "POSTED"),
                stage(stages, "TOTAL"),
                shares(snapshot.total()),
                stage(stages, "FINISHED"),
                shares(snapshot.finished()),
                stage(stages, "READ"),
                snapshot.waitingLong(),
                snapshot.finishedAfterMerge(),
                snapshot.finishedAfterMergeStartedByMerge(),
                snapshot.feedbackWithheldAsMerged());
    }

    private static ReviewTimingStageDTO stage(Map<String, ReviewTimingStage> stages, String name) {
        var stage = stages.getOrDefault(name, EMPTY);
        return new ReviewTimingStageDTO(stage.count(), stage.medianSeconds(), stage.p90Seconds());
    }

    private static @Nullable ReviewTimeSharesDTO shares(@Nullable ReviewTimingSums sums) {
        if (sums == null) {
            return null;
        }
        Double posted = sums.posted();
        double total = sums.asked()
                + sums.queue()
                + sums.preparation()
                + sums.model()
                + sums.runUnsplit()
                + (posted == null ? 0 : posted);
        if (total <= 0) {
            return null;
        }
        return new ReviewTimeSharesDTO(
                sums.asked() / total,
                sums.queue() / total,
                sums.preparation() / total,
                sums.model() / total,
                sums.runUnsplit() / total,
                posted == null ? null : posted / total);
    }
}
