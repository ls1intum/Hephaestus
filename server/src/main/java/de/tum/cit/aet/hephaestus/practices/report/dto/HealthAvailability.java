package de.tum.cit.aet.hephaestus.practices.report.dto;

/**
 * Why an {@link AreaHealthDTO} card does or does not carry counts. One discriminant rather than two booleans,
 * which would have admitted an unrepresentable fourth state.
 *
 * <p>The distinction between {@link #NO_DATA} and {@link #SUPPRESSED} is the honest one: telling a reader
 * "suppressed" when nobody was active would imply people are being hidden, and telling them "no data" when
 * the group was merely too small would imply nothing happened. They are different facts and the UI says so.
 */
public enum HealthAvailability {
    /** Counts are exposed: the group is large enough and no bucket identifies its members. */
    AVAILABLE,
    /**
     * There was activity, but publishing the distribution would risk identifying an individual — either the
     * group is below the anonymity threshold, or one status bucket is small enough (or large enough) to
     * reveal where specific people stand.
     */
    SUPPRESSED,
    /** Nobody had activity on this area in the window. Not a privacy suppression — there is nobody to identify. */
    NO_DATA,
}
