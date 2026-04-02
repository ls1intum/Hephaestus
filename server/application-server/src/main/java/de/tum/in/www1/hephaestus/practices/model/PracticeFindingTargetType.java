package de.tum.in.www1.hephaestus.practices.model;

/**
 * Type of entity that a practice finding targets.
 *
 * <p>Stored as UPPER_CASE via {@code @Enumerated(EnumType.STRING)}, consistent with
 * all other enum columns on {@code practice_finding} ({@code verdict}, {@code severity}).
 *
 * <p>Constrained at the DB level by {@code chk_practice_finding_target_type}.
 * Add new values here <em>and</em> in the CHECK constraint migration when needed.
 *
 * @see PracticeFinding#getTargetType()
 */
public enum PracticeFindingTargetType {
    /** Target is a pull request. */
    PULL_REQUEST,
}
