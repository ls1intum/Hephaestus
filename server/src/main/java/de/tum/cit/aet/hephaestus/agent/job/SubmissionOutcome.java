package de.tum.cit.aet.hephaestus.agent.job;

import de.tum.cit.aet.hephaestus.integration.core.signal.SignalStateReason;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * What one submission attempt did: the job it created or joined, or the reason it created none.
 *
 * <p>The reason is the same controlled one the ledger records, carried out to the caller rather than
 * flattened into an empty result. Anything that has to explain the silence then quotes the decision
 * that was actually taken instead of restating the reasons it guesses are likely — the two drift, and
 * a wrong reason is worse than a missing one because it sends the reader to the wrong fix.
 */
record SubmissionOutcome(@Nullable AgentJob job, @Nullable SignalStateReason refusal) {
    SubmissionOutcome {
        if ((job == null) == (refusal == null)) {
            throw new IllegalArgumentException("A submission either produced a job or a reason it produced none");
        }
    }

    static SubmissionOutcome of(AgentJob job) {
        return new SubmissionOutcome(Objects.requireNonNull(job, "job"), null);
    }

    static SubmissionOutcome refused(SignalStateReason refusal) {
        return new SubmissionOutcome(null, Objects.requireNonNull(refusal, "refusal"));
    }

    /** The refusal, for callers that have already established there is no job. */
    SignalStateReason requireRefusal() {
        return Objects.requireNonNull(refusal, "refusal");
    }
}
