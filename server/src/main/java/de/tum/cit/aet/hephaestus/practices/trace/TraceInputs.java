package de.tum.cit.aet.hephaestus.practices.trace;

import de.tum.cit.aet.hephaestus.integration.core.signal.SignalName;
import de.tum.cit.aet.hephaestus.integration.core.signal.SignalState;
import de.tum.cit.aet.hephaestus.integration.core.signal.SignalStateReason;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackSuppressionReason;
import de.tum.cit.aet.hephaestus.practices.model.PracticeReviewTier;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * The facts a trace is derived from, flattened out of their entities.
 *
 * <p>Plain values so {@link PracticeTraceDeriver}, which holds every judgement in this feature, can be
 * exercised without a database, a Spring context or a mirror.
 */
final class TraceInputs {

    private TraceInputs() {}

    /**
     * A practice as the trace sees it.
     *
     * @param dormancyReason why nothing connected here can raise what it watches, or {@code null} when
     *                      something can; the sentence comes straight from {@code DormantBinding}
     */
    record TracedPractice(
        Long id,
        String slug,
        String name,
        PracticeReviewTier reviewTier,
        List<SignalName> watches,
        @Nullable String dormancyReason
    ) {}

    /**
     * One ledger row.
     *
     * @param id the row's own identity. Carried because a signal name cannot identify the occurrence an
     *           answer rests on: the same signal recurs on every new revision, and "assessed when new
     *           commits were pushed" is a different claim depending on which push
     */
    record SignalOccurrence(
        UUID id,
        SignalName signal,
        Instant occurredAt,
        SignalState state,
        @Nullable SignalStateReason stateReason,
        @Nullable UUID reviewId
    ) {}

    /** What one practice produced on this artifact, across every run of it. */
    record PracticeOutput(
        int observations,
        int delivered,
        List<FeedbackSuppressionReason> withheldReasons,
        @Nullable UUID latestReviewId,
        @Nullable Instant latestObservedAt
    ) {
        static final PracticeOutput NONE = new PracticeOutput(0, 0, List.of(), null, null);
    }
}
