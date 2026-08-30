package de.tum.cit.aet.hephaestus.agent.job;

import de.tum.cit.aet.hephaestus.integration.core.signal.SignalStateReason;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

record SubmissionOutcome(@Nullable AgentJob job, @Nullable SignalStateReason refusal, boolean created) {
    SubmissionOutcome {
        if ((job == null) == (refusal == null)) {
            throw new IllegalArgumentException("A submission either produced a job or a reason it produced none");
        }
        if (created && job == null) {
            throw new IllegalArgumentException("Only a submission with a job can be newly created");
        }
    }

    static SubmissionOutcome created(AgentJob job) {
        return new SubmissionOutcome(Objects.requireNonNull(job, "job"), null, true);
    }

    static SubmissionOutcome joined(AgentJob job) {
        return new SubmissionOutcome(Objects.requireNonNull(job, "job"), null, false);
    }

    static SubmissionOutcome refused(SignalStateReason refusal) {
        return new SubmissionOutcome(null, Objects.requireNonNull(refusal, "refusal"), false);
    }

    SignalStateReason requireRefusal() {
        return Objects.requireNonNull(refusal, "refusal");
    }
}
