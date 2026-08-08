package de.tum.cit.aet.hephaestus.practices.model;

import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackChannel;

/**
 * How a measurement came to be taken — the provenance axis a trend line must never mix.
 *
 * <p>Reading a live series and a backfilled one as one series manufactures change nobody made: a
 * workspace that backfills its history on the day it adopts Hephaestus would show a dramatic day-one
 * improvement that is entirely an artefact of when the two populations were sampled.
 *
 * <p>Recorded per observation rather than per job so the exclusion survives every later read: the
 * aggregate queries do not join job metadata, and a column that must be joined to be honoured will be
 * forgotten.
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
     * and a unit travels only where <em>both</em> admit it. The two are not merged because they answer
     * different questions: the tier is a policy an admin sets, this is a fact about the measurement.
     *
     * <p>{@link #BACKFILL} is entitled to {@link FeedbackChannel#PROFILE} and nothing else. Posting a
     * backfilled finding in context would notify everyone subscribed to a merged pull request about work
     * nobody can act on; raising it in a mentor turn would coach a developer about a months-old decision
     * as though it were today's. Written as this derivation rather than {@code return false} so that
     * building a PROFILE producer makes it correct without an edit.
     */
    public boolean delivers(FeedbackChannel channel) {
        return switch (this) {
            case LIVE, MANUAL -> true;
            case BACKFILL -> channel == FeedbackChannel.PROFILE;
        };
    }
}
