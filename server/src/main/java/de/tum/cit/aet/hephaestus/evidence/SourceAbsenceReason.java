package de.tum.cit.aet.hephaestus.evidence;

/**
 * Why a source is not present in a capture.
 *
 * <p>An availability state records <em>that</em> a source is absent; this records <em>why</em>, which is
 * what makes the absence actionable for an operator, an auditor, or the review prompt.
 */
public enum SourceAbsenceReason {
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
    /** {@code UNAVAILABLE}: this repository has no working copy, so nothing could be read at any commit. */
    NO_WORKING_COPY,
    /** {@code UNAVAILABLE}: the requested conversation or artifact does not exist upstream. */
    NOT_FOUND,
    /**
     * {@code UNAVAILABLE}: the mirror holds the row but not its content, which it dropped under a size
     * cap.
     *
     * <p>Deliberately distinct from {@link #NOT_FOUND}: the artifact exists and the operator's fix is to
     * raise the cap, not to go looking for something deleted. Previously reported as the minimisation
     * reason, which sent that operator to a consent-and-retention control that had nothing to do with it.
     */
    CONTENT_EVICTED,
    /** {@code REDACTED}: withheld by policy rather than by a subject's choice. */
    PRIVACY_POLICY,
    /** {@code REDACTED}: the people in it have not granted, or have withdrawn, consent to read it. */
    CONSENT_NOT_ACTIVE,
    /** {@code COLLECTION_ERROR}: the collector failed; the source's true content is unknown. */
    PROVIDER_FAILURE,
}
