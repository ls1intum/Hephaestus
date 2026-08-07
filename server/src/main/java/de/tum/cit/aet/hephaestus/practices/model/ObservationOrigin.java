package de.tum.cit.aet.hephaestus.practices.model;

import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackChannel;

/**
 * How a measurement came to be taken — the provenance axis a trend line must never mix.
 *
 * <p>Two observations of the same practice on the same artifact can disagree purely because one was
 * taken as the work happened and the other was taken later, in bulk, over a corpus that was selected
 * with hindsight. Reading them as one series manufactures change that nobody made: a workspace that
 * backfills six months of history on the day it adopts Hephaestus would show a dramatic day-one
 * improvement, which is entirely an artefact of when the two populations were sampled.
 *
 * <p>Recorded per observation rather than per job so the exclusion survives every later read — a job's
 * metadata is not joined by the aggregate queries, and a column that has to be joined to be honoured is
 * a column that will be forgotten.
 */
public enum ObservationOrigin {
    /**
     * Taken by a review that ran in response to the work itself. The only origin whose population is
     * defined by what developers did rather than by what an operator selected, and therefore the default
     * for every behavioural read.
     */
    LIVE,
    /**
     * Taken because somebody asked for this review by hand. Real measurement, but a self-selected
     * sample — people ask about work they are unsure of — so it is separable from LIVE by design.
     */
    MANUAL,
    /**
     * Taken by a sweep over artifacts that already existed when the sweep started. Sound as a snapshot,
     * unusable as a trend against LIVE rows.
     */
    BACKFILL;

    /**
     * Whether a measurement of this provenance may be <em>said out loud</em> on the given channel.
     *
     * <p>The second half of the delivery predicate; {@link PracticeReviewTier#delivers} is the first,
     * and a unit travels only where <em>both</em> admit it. The two axes answer different questions and
     * are deliberately not merged: the tier is the workspace's standing policy on how loud a practice
     * may be, and this is a fact about the measurement itself.
     *
     * <p>{@link #BACKFILL} is entitled to {@link FeedbackChannel#PROFILE} and to nothing else. Posting a
     * backfilled finding in context would comment on a merged pull request — notifying everyone
     * subscribed to it about work nobody can act on any more — and raising it in a mentor turn would
     * coach a developer about a decision they made months ago as though it were today's. Aggregating it
     * on the recipient's own dashboard is the one delivery that is honest about being retrospective.
     *
     * <p><strong>PROFILE has no producer</strong> (see {@link PracticeReviewTier#delivers}), so the
     * conjunction is empty for every tier and a backfilled observation is, today, measured and delivered
     * nowhere. That is stated as a derivation rather than as {@code return false}: the day somebody
     * builds a PROFILE producer, the tier's own tripwire fires first and this rule is already correct.
     */
    public boolean delivers(FeedbackChannel channel) {
        return switch (this) {
            // The tier alone decides for a measurement taken of work as it happened or on request.
            case LIVE, MANUAL -> true;
            case BACKFILL -> channel == FeedbackChannel.PROFILE;
        };
    }
}
