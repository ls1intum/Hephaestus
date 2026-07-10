package de.tum.cit.aet.hephaestus.core.audit;

/**
 * The kind of data disclosure recorded by a {@link DataAccessEvent}. Stored as a string so the audit trail
 * stays readable and new surfaces can be added without a numeric-ordinal migration hazard.
 */
public enum DataAccessResourceType {
    /** A named developer's own practice report was opened (subject = that developer). */
    PRACTICE_REPORT,
    /** The practice-report roster (which names developers) was listed (subject = NULL: a bulk view). */
    PRACTICE_ROSTER,
}
