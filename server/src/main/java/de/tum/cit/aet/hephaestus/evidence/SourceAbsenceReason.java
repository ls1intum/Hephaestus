package de.tum.cit.aet.hephaestus.evidence;

/**
 * Why a source is not present in a capture.
 *
 * <p>The availability states are only half the answer: "absent" is actionable for a reader —
 * operator, auditor, or the model's prompt — only when it says <em>why</em>. A free-form code lets
 * every new collector invent its own vocabulary, so downstream code cannot branch on the reason and
 * "deliberately minimised" collapses into "the provider fell over". Each constant below names one
 * cause and states which availability it belongs with.
 */
public enum SourceAbsenceReason {
    /** {@code NOT_COLLECTED}: the practice did not ask for this source, so it was never read. */
    MINIMIZED,
    /** {@code NOT_COLLECTED}: no unexpired use decision permits this source for this purpose. */
    GOVERNANCE_NOT_EFFECTIVE,
    /** {@code NOT_COLLECTED}: collection is switched off for this source in this deployment. */
    DISABLED,
    /** {@code UNAVAILABLE}: this deployment ships no collector for the source. */
    NO_PROVIDER,
    /** {@code UNAVAILABLE}: the collector reported empty for a source whose contract forbids empty. */
    EMPTY_NOT_VALID,
    /** {@code UNAVAILABLE}: the pinned commit the capture is anchored to is gone from the mirror. */
    PINNED_HEAD_MISSING,
    /** {@code UNAVAILABLE}: the requested conversation or artifact does not exist upstream. */
    NOT_FOUND,
    /** {@code REDACTED}: withheld by policy rather than by a subject's choice. */
    PRIVACY_POLICY,
    /** {@code REDACTED}: the people in it have not granted, or have withdrawn, consent to read it. */
    CONSENT_NOT_ACTIVE,
    /** {@code COLLECTION_ERROR}: the collector failed; the source's true content is unknown. */
    PROVIDER_FAILURE,
}
