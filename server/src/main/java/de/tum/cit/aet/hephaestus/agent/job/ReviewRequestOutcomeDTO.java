package de.tum.cit.aet.hephaestus.agent.job;

import de.tum.cit.aet.hephaestus.integration.core.signal.SignalStateReason;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;
import org.jspecify.annotations.NonNull;

/**
 * What came of asking for a review: the run it started, or the reason it started none.
 *
 * <p>A refusal is a 200 carrying this body, not a 4xx: nearly every reason a requested review does not
 * run is a workspace condition the asker can neither see nor fix — an exhausted budget, a practice
 * turned Off, a cooldown — so an error status would call the ask broken when something nameable simply
 * stopped it. The one answer that is a status code is a caller with no standing on the artifact, a 403.
 */
@Schema(description = "What came of asking for a review")
public record ReviewRequestOutcomeDTO(
    @NonNull @Schema(description = "Whether a review is now running, or nothing was started") Status status,
    @Schema(description = "The review that is now running; absent when none was started") UUID jobId,
    @Schema(description = "The controlled-vocabulary reason nothing was started; absent when a review was started")
    SignalStateReason reason,
    @Schema(
        description = "The reason as one sentence for the person who asked. Render it verbatim: it is " +
            "written next to the reason it explains so that every surface says the same thing, and a " +
            "re-worded copy is how a screen and a support answer come to disagree."
    )
    String reasonDescription
) {
    /** The DTO's own vocabulary; {@code FORBIDDEN} never reaches a body, since it is answered as a 403. */
    public enum Status {
        /** A review is running, or an identical one already was and this ask joined it. */
        SUBMITTED,
        /** Understood, and stopped by something nameable. {@link #reason} says what. */
        REFUSED,
    }

    static ReviewRequestOutcomeDTO from(ManualReviewOutcome outcome) {
        return new ReviewRequestOutcomeDTO(
            outcome.status() == ManualReviewOutcome.Status.SUBMITTED ? Status.SUBMITTED : Status.REFUSED,
            outcome.jobId(),
            outcome.reason(),
            outcome.describeReason()
        );
    }
}
