package de.tum.cit.aet.hephaestus.practices.model;

/**
 * Whether a {@link Practice} describes behaviour we want to see, behaviour we want to avoid, or both.
 *
 * <p>PracticeKind supplies the good/bad <em>direction</em> that {@link Presence} deliberately omits (see
 * ADR 0021, F-6). It is what makes a {@code Presence.OBSERVED} mean "strength" for one practice and
 * "problem" for another, instead of overloading the observation value:
 *
 * <ul>
 *   <li>{@code GOOD_PRACTICE} — a good habit ("avoid hardcoded secrets", "ship tests with behaviour
 *       changes"). {@code OBSERVED} = strength; {@code NOT_OBSERVED} = the gap to coach.</li>
 *   <li>{@code BAD_PRACTICE} — an anti-pattern phrased as the bad behaviour itself. {@code OBSERVED}
 *       = the problem to coach; {@code NOT_OBSERVED} = clean.</li>
 *   <li>{@code CONTEXTUAL} — context decides (e.g. a pattern that is fine in tests but a smell in
 *       production). The detector's per-finding severity carries the direction case by case.</li>
 * </ul>
 *
 * <p>Every practice in the catalogue today is framed as a good practice, so {@code GOOD_PRACTICE} is
 * the default; {@code BAD_PRACTICE}/{@code CONTEXTUAL} exist so future anti-pattern detectors need no
 * schema change.
 */
public enum PracticeKind {
    /** A good habit: {@code OBSERVED} is a strength, {@code NOT_OBSERVED} is the coachable gap. */
    GOOD_PRACTICE,
    /** An anti-pattern stated as the bad behaviour: {@code OBSERVED} is the problem, {@code NOT_OBSERVED} is clean. */
    BAD_PRACTICE,
    /** Direction depends on context; per-finding severity carries it case by case. */
    CONTEXTUAL;

    /**
     * Whether a {@code observation} under this kind is a <em>problem</em> the developer should act on.
     * This is the single source of truth for the "is this a problem?" decision so a sign-neutral observation
     * can mean a problem for one practice and a strength for another (ADR 0021, F-6):
     *
     * <ul>
     *   <li>{@code GOOD_PRACTICE}/{@code CONTEXTUAL}: {@code NOT_OBSERVED} (missed the good habit) is the problem.</li>
     *   <li>{@code BAD_PRACTICE}: {@code OBSERVED} (did the bad thing) is the problem.</li>
     * </ul>
     *
     * {@code NOT_APPLICABLE} is never a problem (the practice did not apply).
     */
    public boolean isProblem(Presence observation) {
        return this == BAD_PRACTICE ? observation == Presence.OBSERVED : observation == Presence.NOT_OBSERVED;
    }

    /**
     * Whether a {@code observation} under this kind is a <em>strength</em> worth acknowledging — the exact
     * inverse of {@link #isProblem(Presence)} over the present observations ({@code NOT_APPLICABLE} is neither).
     */
    public boolean isStrength(Presence observation) {
        return this == BAD_PRACTICE ? observation == Presence.NOT_OBSERVED : observation == Presence.OBSERVED;
    }
}
