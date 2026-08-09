package de.tum.cit.aet.hephaestus.agent.job;

import de.tum.cit.aet.hephaestus.integration.core.signal.SignalStateReason;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * What came of a review somebody asked for: the run it started, or the reason it started none.
 *
 * <p>A refusal is a first-class answer here rather than an error. Almost every reason a requested review
 * does not run is a condition of the workspace the requester can neither see nor fix from where they
 * stand — an exhausted budget, a cooldown, a practice turned down to Off — and answering those with a
 * failure would tell them the button is broken. It is not: the ask was understood, and something
 * nameable stopped it. The name travels out with {@link SignalStateReason#describe()} so every surface
 * says the same sentence.
 *
 * <p>{@link Status#FORBIDDEN} is the one answer that is not about the workspace's state. It is kept in
 * this vocabulary rather than thrown so that a caller which cannot raise an HTTP status — the SCM bot
 * command — has somewhere to put it.
 */
public record ManualReviewOutcome(Status status, @Nullable UUID jobId, @Nullable SignalStateReason reason) {
    public enum Status {
        /** A review is running, or an identical one already was and this ask joined it. */
        SUBMITTED,
        /** Understood, and stopped by something nameable. {@link #reason} says what. */
        REFUSED,
        /** The asker has no standing on this artifact. See {@link ReviewRequestAuthority}. */
        FORBIDDEN,
    }

    public ManualReviewOutcome {
        if ((status == Status.SUBMITTED) != (jobId != null)) {
            throw new IllegalArgumentException("A submitted request names its job, and only a submitted one does");
        }
        if ((status == Status.REFUSED) != (reason != null)) {
            throw new IllegalArgumentException("A refused request names its reason, and only a refused one does");
        }
    }

    static ManualReviewOutcome submitted(UUID jobId) {
        return new ManualReviewOutcome(Status.SUBMITTED, Objects.requireNonNull(jobId, "jobId"), null);
    }

    static ManualReviewOutcome refused(SignalStateReason reason) {
        return new ManualReviewOutcome(Status.REFUSED, null, Objects.requireNonNull(reason, "reason"));
    }

    static ManualReviewOutcome forbidden() {
        return new ManualReviewOutcome(Status.FORBIDDEN, null, null);
    }

    /** The sentence to show whoever asked, or empty when there is nothing to explain. */
    public @Nullable String describeReason() {
        return reason == null ? null : reason.describe();
    }
}
