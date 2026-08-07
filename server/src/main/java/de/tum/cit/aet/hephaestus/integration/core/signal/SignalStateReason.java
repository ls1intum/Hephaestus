package de.tum.cit.aet.hephaestus.integration.core.signal;

/**
 * Why a recorded signal ended up in the state it did.
 *
 * <p>A controlled vocabulary rather than free text so that "how many reviews did this instance not
 * run last week, and why" is a {@code GROUP BY}. Each reason also decides its own resulting state,
 * which keeps the retryable/terminal judgement in one place instead of at every refusal site: a
 * reason is retryable exactly when an operator can lift it without the artifact changing.
 */
public enum SignalStateReason {
    /** The workspace's own gate declined; its detail is logged, and the class of answer is stable. */
    GATE_SKIPPED(SignalState.SUPPRESSED),

    /** Rate limiting, not correctness. Retrying later would defeat the limit the workspace asked for. */
    COOLDOWN_ACTIVE(SignalState.SUPPRESSED),

    /** Another submission for the same subject won the idempotency race; it carries the review. */
    CONCURRENT_DUPLICATE(SignalState.SUPPRESSED),

    /**
     * The artifact falls outside the workspace's review scope — the wrong target branch, or a repository
     * the workspace syncs but does not review.
     *
     * <p>Terminal rather than pending, unlike the tier reasons: the facts it turns on belong to the
     * artifact and do not change. A merge request that targeted {@code develop} will still have targeted
     * {@code develop} tomorrow, so re-offering it would be the reaper burning cycles on a decision that
     * cannot come out differently. Widening the scope changes what happens NEXT, not what already did.
     */
    OUT_OF_REVIEW_SCOPE(SignalState.SUPPRESSED),

    WORKSPACE_INACTIVE(SignalState.PENDING),

    PRACTICES_DISABLED(SignalState.PENDING),

    NO_ACTIVE_PRACTICE(SignalState.PENDING),

    /** No enabled practice-review binding, or it lost its enablement between discovery and submission. */
    BINDING_DISABLED(SignalState.PENDING),

    /**
     * A practice IS bound to this signal, and every one that is sits at loudness tier {@code OFF} — the
     * workspace turned the review down to silence rather than never having asked for it.
     *
     * <p>Separate from {@link #NO_ACTIVE_PRACTICE} on purpose: that reason means the workspace has no
     * practice for this work at all, while this one means it has one and chose not to run it. Collapsing
     * them would make "we are deliberately not reviewing this" indistinguishable from "nobody ever set
     * this up", which are the two answers an operator most needs told apart. Retryable, because raising
     * the tier lifts it without the artifact changing.
     */
    PRACTICE_TIER_OFF(SignalState.PENDING),

    /** The purse funding this binding is exhausted; it refills. */
    BUDGET_EXHAUSTED(SignalState.PENDING),

    /** The bound model left the catalog. An admin re-pointing the binding revives every pending signal. */
    MODEL_UNAVAILABLE(SignalState.PENDING),

    /** Pending for longer than the ledger keeps offering it. */
    PENDING_DEADLINE_EXCEEDED(SignalState.LAPSED),

    /** The artifact was deleted while its signal waited, so there is nothing left to review. */
    ARTIFACT_GONE(SignalState.LAPSED);

    private final SignalState resultingState;

    SignalStateReason(SignalState resultingState) {
        this.resultingState = resultingState;
    }

    public SignalState resultingState() {
        return resultingState;
    }

    /** True when the reaper should keep re-offering the signal. */
    public boolean isRetryable() {
        return resultingState == SignalState.PENDING;
    }
}
