package de.tum.cit.aet.hephaestus.practices.spi;

/**
 * Why a review session that stopped as {@link ScriptStopReason#OFF_FORMAT} has no result, where the runner's typed
 * counts or the built-in's contract prove more than the stop reason does.
 */
public enum ScriptNoResultCause {
    /** The evidence check refused its result at least once because a required or cited source was not read. */
    EVIDENCE_NOT_READ,
    /**
     * Its review session ended without an accepted result, with no refusal of the call's shape counted. The built-in
     * only calls the review, so this is all that its stop reason proves.
     */
    SESSION_ENDED
}
