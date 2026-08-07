package de.tum.cit.aet.hephaestus.practices.model;

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
     * unusable as a trend against LIVE rows, and the reason this column exists before anything writes it.
     */
    BACKFILL,
}
