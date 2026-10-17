package de.tum.cit.aet.hephaestus.practices.spi;

import java.util.Map;
import org.jspecify.annotations.Nullable;

public record ReviewTimingSnapshot(
        Map<String, ReviewTimingStage> stages,
        @Nullable ReviewTimingSums total,
        @Nullable ReviewTimingSums finished,
        long waitingLong,
        long finishedAfterMerge,
        long finishedAfterMergeStartedByMerge,
        long feedbackWithheldAsMerged) {
    public ReviewTimingSnapshot {
        stages = Map.copyOf(stages);
    }
}
