package de.tum.cit.aet.hephaestus.agent.job;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

@Schema(description = "The practice review queue of a workspace as it stands now, and how fast it empties")
public record PracticeReviewQueueDTO(
        @NonNull @Schema(description = "When the queue was read; every wait and throughput window ends here")
        Instant asOf,

        @NonNull @Schema(description = "Practice reviews running now")
        Long running,

        @NonNull
        @Schema(
                description = "Distinct workers running this workspace's practice reviews now, as the running jobs"
                        + " record them")
        Long activeWorkers,

        @NonNull @Schema(description = "Practice reviews queued now, other than those held for budget")
        Long waiting,

        @Nullable
        @Schema(
                description = "When the longest waiting of those was requested; absent when none waits. Holds for"
                        + " budget are left out, since they wait for the cap and not for a worker")
        Instant oldestWaitingSince,

        @NonNull
        @Schema(description = "Practice reviews queued now and held because the payer is over its monthly LLM cap")
        Long heldForBudget,

        @NonNull
        @Schema(
                description = "The head of the queue: the running reviews first, longest running first, then the"
                        + " waiting ones, longest waiting first, then the budget holds. At most "
                        + PracticeReviewQueueService.HEAD_SIZE + " entries")
        List<PracticeReviewQueueItemDTO> head,

        @NonNull
        @Schema(
                description = "Practice reviews that finished in the hour before asOf, completed, failed or timed"
                        + " out; cancelled reviews are not counted")
        Long finishedLastHour,

        @NonNull @Schema(description = "Practice reviews that finished in the 24 hours before asOf, counted alike")
        Long finishedLastDay,

        @NonNull @Schema(description = "finishedLastDay per hour, the throughput of the last 24 hours")
        Double finishedPerHourLastDay) {}
