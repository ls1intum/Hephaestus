package de.tum.cit.aet.hephaestus.agent.job;

import de.tum.cit.aet.hephaestus.practices.reviewoutput.dto.ReviewSubjectDTO;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

@Schema(description = "One practice review queued or running now")
public record PracticeReviewQueueItemDTO(
        @NonNull @Schema(description = "The review") UUID id,

        @NonNull
        @Schema(
                description = "QUEUED or RUNNING",
                allowableValues = {"QUEUED", "RUNNING"})
        AgentJobStatus status,

        @NonNull @Schema(description = "True when the review is queued and held for budget")
        Boolean heldForBudget,

        @NonNull ReviewRunTargetDTO target,

        @Nullable
        @Schema(description = "The developer the work is about, as the review names them; absent when it names none")
        ReviewSubjectDTO developer,

        @NonNull
        @Schema(
                description = "Since when it is in its status: started for a running review; for a queued one, since it"
                        + " became available to a worker, which is its request unless it was requeued; for one held"
                        + " for budget, its request")
        Instant since) {}
