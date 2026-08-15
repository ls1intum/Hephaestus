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
    /** {@code UNAVAILABLE}: this repository has no working copy, so nothing could be read at any commit. */
    NO_WORKING_COPY,
    /** {@code UNAVAILABLE}: the requested conversation or artifact does not exist upstream. */
    NOT_FOUND,
    /**
     * {@code UNAVAILABLE}: the mirror holds the row but not its content, which it dropped under a size
     * cap.
     *
     * <p>Deliberately distinct from {@link #NOT_FOUND}: the artifact exists and the operator's fix is to
     * raise the cap, not to go looking for something deleted. A privacy reason here would be wrong twice
     * over — it sends that operator to a consent-and-retention control, and it claims a minimisation
     * decision nobody took.
     */
    CONTENT_EVICTED,
    /** {@code REDACTED}: the people in it have not granted, or have withdrawn, consent to read it. */
    CONSENT_NOT_ACTIVE,
    /** {@code COLLECTION_ERROR}: the collector failed; the source's true content is unknown. */
    PROVIDER_FAILURE,
}
