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

    /**
     * The workspace's cooldown, applied to asking rather than to running: somebody already asked for a
     * review of this artifact inside the window.
     *
     * <p>Its own reason rather than {@link #COOLDOWN_ACTIVE} because the two are true at different
     * times and send the reader to different places. {@code COOLDOWN_ACTIVE} says a review <em>ran</em>
     * recently, so waiting produces one about newer work; this one says an <em>ask</em> was made
     * recently, and that ask may itself have been refused — telling the asker a review ran would send
     * them looking for feedback that does not exist.
     */
    REQUEST_COOLDOWN_ACTIVE(SignalState.SUPPRESSED),

    /**
     * The person asking has spent their hour's allowance of hand-requested reviews.
     *
     * <p>The one limit here that is about a person rather than an artifact. Every other rate limit is
     * keyed on the work, so asking for one review each of twenty colleagues' merge requests passes all
     * of them while being precisely the pattern that turns a coaching tool into a way to nag a team.
     */
    REQUESTER_QUOTA_EXHAUSTED(SignalState.SUPPRESSED),

    /** Another submission for the same subject won the idempotency race; it carries the review. */
    CONCURRENT_DUPLICATE(SignalState.SUPPRESSED),

    /**
     * The artifact falls outside the workspace's review scope — the wrong target branch, or a repository
     * the workspace syncs but does not review.
     *
     * <p>Terminal rather than pending, unlike the tier reasons: it turns on facts that belong to the
     * artifact and cannot change, so widening the scope alters what happens next, not what already did.
     */
    OUT_OF_REVIEW_SCOPE(SignalState.SUPPRESSED),

    WORKSPACE_INACTIVE(SignalState.PENDING),

    PRACTICES_DISABLED(SignalState.PENDING),

    NO_ACTIVE_PRACTICE(SignalState.PENDING),

    /**
     * The workspace has no enabled AI model bound to practice review, or it lost that binding between
     * discovery and submission.
     *
     * <p>Named for the model rather than the practice because that is what an operator has to go and
     * fix: this is Administration &rarr; AI models, not the practice catalogue. A name built around the
     * binding reads as a fact about a practice binding and sends the reader to the wrong screen.
     */
    REVIEW_MODEL_UNBOUND(SignalState.PENDING),

    /**
     * A practice is bound to this signal and every one that is sits at loudness tier {@code OFF}.
     *
     * <p>Separate from {@link #NO_ACTIVE_PRACTICE} on purpose: collapsing them would make "we are
     * deliberately not reviewing this" indistinguishable from "nobody ever set this up".
     */
    PRACTICE_TIER_OFF(SignalState.PENDING),

    /** The purse funding this binding is exhausted; it refills. */
    BUDGET_EXHAUSTED(SignalState.PENDING),

    /**
     * The artifact exists but nobody it could be attributed to resolves to a workspace member — the
     * author has not linked the account this vendor knows them by.
     *
     * <p>Its own reason rather than a gate skip because it is retryable: the person can link the account
     * afterwards, and everything of theirs that was passed over then becomes reviewable without anything
     * upstream happening again. {@link #GATE_SKIPPED} would make it terminal and lose that silently.
     */
    SUBJECT_UNLINKED(SignalState.PENDING),

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

    /**
     * Whether this reason leaves the signal open for the reaper to re-offer — a restatement of
     * {@link #resultingState()}, not a second source of truth. The reaper selects on the stored state.
     */
    public boolean isRetryable() {
        return resultingState == SignalState.PENDING;
    }

    /**
     * One sentence, in the reader's words, for every surface that has to explain a silence.
     *
     * <p>Beside the reason rather than at each surface that renders it, for the same argument the
     * vocabulary itself rests on: a reason and the sentence explaining it are one fact, and a second
     * copy of it is a second thing to keep true. A hand-written second copy is how a refusal that was
     * a cooldown came to be reported as an exhausted budget — which sends an operator to raise a cap
     * that was never set.
     */
    public String describe() {
        return switch (this) {
            case GATE_SKIPPED -> "The workspace's review gate declined this occurrence.";
            case COOLDOWN_ACTIVE -> "Another review ran on this artifact inside the workspace's cooldown window.";
            case REQUEST_COOLDOWN_ACTIVE -> "A review of this was already asked for inside the workspace's cooldown window.";
            case REQUESTER_QUOTA_EXHAUSTED -> "You have asked for as many reviews as an hour allows; the allowance refills.";
            case CONCURRENT_DUPLICATE -> "Another submission for the same work carries this review.";
            case OUT_OF_REVIEW_SCOPE -> "This artifact is outside the branches and repositories this workspace reviews.";
            case WORKSPACE_INACTIVE -> "The workspace was not active; it is re-offered when the workspace is.";
            case PRACTICES_DISABLED -> "Practice review is switched off for this workspace; it is re-offered when it is switched on.";
            case NO_ACTIVE_PRACTICE -> "No practice was bound to this occurrence when it was recorded.";
            case REVIEW_MODEL_UNBOUND -> "No AI model is bound to practice review for this workspace; binding one in Administration re-offers it.";
            case PRACTICE_TIER_OFF -> "Every practice bound to this occurrence sits at Off; raising one re-offers it.";
            case BUDGET_EXHAUSTED -> "The budget funding this review was exhausted; it is re-offered when the budget refills.";
            case SUBJECT_UNLINKED -> "Nobody this could be attributed to has linked their account; linking one re-offers it.";
            case MODEL_UNAVAILABLE -> "The model this review is bound to left the catalog; re-pointing the binding re-offers it.";
            case PENDING_DEADLINE_EXCEEDED -> "It waited longer than the ledger keeps re-offering a signal.";
            case ARTIFACT_GONE -> "The artifact was deleted before a review could run.";
        };
    }
}
