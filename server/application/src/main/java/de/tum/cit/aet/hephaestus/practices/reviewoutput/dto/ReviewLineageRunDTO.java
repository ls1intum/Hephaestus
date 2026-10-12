package de.tum.cit.aet.hephaestus.practices.reviewoutput.dto;

import de.tum.cit.aet.hephaestus.practices.spi.ReviewOutcomeLookup.ReviewRunState;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewRunLookup.ReviewRunConclusion;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewRunLookup.ReviewRunPhase;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

@Schema(description = "The review in a lineage")
public record ReviewLineageRunDTO(
        @NonNull UUID id,
        @NonNull ReviewRunState status,

        @NonNull @Schema(description = "When the review was asked for")
        Instant createdAt,

        @Schema(description = "The language model the review used; null when it recorded none") @Nullable
        String model,

        @Schema(description = "When the review finished; null while it runs") @Nullable
        Instant finishedAt,

        @NonNull
        @Schema(description = "Where the review is, or how it ended, finer than status: a timeout or a cancellation")
        ReviewRunPhase phase,

        @Schema(
                description = "When a worker picked the review up; null while it waits. It can be well after createdAt,"
                        + " the time it was asked for")
        @Nullable
        Instant startedAt,

        @Schema(description = "Why a queued review waits, where an admin can undo it, such as BUDGET; null otherwise")
        @Nullable
        String holdReason,

        @Schema(
                description = "Why a completed review recorded what it did: REVIEWED when the model ran,"
                        + " INSUFFICIENT_EVIDENCE when evidence could not be read and nothing was assessed, COALESCED"
                        + " when an earlier review on the same code had answered every practice, SUPERSEDED when a"
                        + " newer admitted review of the same work replaced it while it waited. Null until the review"
                        + " completed")
        @Nullable
        ReviewRunConclusion outcome) {}
