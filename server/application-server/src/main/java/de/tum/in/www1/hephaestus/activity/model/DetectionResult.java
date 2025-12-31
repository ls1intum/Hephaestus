package de.tum.in.www1.hephaestus.activity.model;

/**
 * Result of a bad practice detection operation.
 *
 * <p>Returned by the detection service to indicate the outcome
 * of analyzing a pull request for potential issues.
 */
public enum DetectionResult {
    /** Detection completed and found one or more bad practices */
    BAD_PRACTICES_DETECTED,
    /** Detection completed but found no issues */
    NO_BAD_PRACTICES_DETECTED,
    /** Detection failed because the pull request could not be found or updated */
    ERROR_NO_UPDATE_ON_PULLREQUEST,
}
