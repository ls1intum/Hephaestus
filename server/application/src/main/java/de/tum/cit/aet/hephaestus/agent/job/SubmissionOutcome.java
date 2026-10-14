package de.tum.cit.aet.hephaestus.agent.job;

import de.tum.cit.aet.hephaestus.integration.core.spi.ItemEventReason;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

record SubmissionOutcome(@Nullable ReviewJob job, @Nullable ItemEventReason refusal, boolean created) {
    SubmissionOutcome {
        if ((job == null) == (refusal == null)) {
            throw new IllegalArgumentException("A submission either produced a job or a reason it produced none");
        }
        if (created && job == null) {
            throw new IllegalArgumentException("Only a submission with a job can be newly created");
        }
    }

    static SubmissionOutcome created(ReviewJob job) {
        return new SubmissionOutcome(Objects.requireNonNull(job, "job"), null, true);
    }

    static SubmissionOutcome joined(ReviewJob job) {
        return new SubmissionOutcome(Objects.requireNonNull(job, "job"), null, false);
    }

    static SubmissionOutcome refused(ItemEventReason refusal) {
        return new SubmissionOutcome(null, Objects.requireNonNull(refusal, "refusal"), false);
    }

    ItemEventReason requireRefusal() {
        return Objects.requireNonNull(refusal, "refusal");
    }
}
