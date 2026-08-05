package de.tum.cit.aet.hephaestus.evidence;

/**
 * Why a source is not present in a capture.
 *
 * <p>An availability state records that a source is absent; this records why, which is what makes
 * the absence actionable for an operator, an auditor, or the review prompt. A free-form code would
 * let each collector introduce its own vocabulary, leaving downstream code unable to branch on the
 * reason and collapsing a deliberate minimisation into an indistinguishable collection failure. Each
 * constant names one cause and states the availability state it accompanies.
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
