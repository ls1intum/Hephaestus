package de.tum.cit.aet.hephaestus.agent.job;

import de.tum.cit.aet.hephaestus.integration.core.signal.SignalStateReason;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * What came of a review somebody asked for: the run it started, or the reason it started none, refused
 * rather than errored, with the reason exposed via {@link SignalStateReason#describe()} so every surface
 * reports the same sentence.
 *
 * <p>{@link Status#FORBIDDEN} lives in this vocabulary instead of being thrown, since some callers — the
 * SCM bot command — cannot raise an HTTP status.
 */
public record ManualReviewOutcome(
        Status status, @Nullable UUID jobId, @Nullable SignalStateReason reason) {
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
