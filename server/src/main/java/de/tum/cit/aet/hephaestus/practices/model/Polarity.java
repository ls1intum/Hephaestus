package de.tum.cit.aet.hephaestus.practices.model;

/**
 * Whether a {@link Practice} describes behaviour we want to see, behaviour we want to avoid, or both.
 *
 * <p>Polarity supplies the good/bad <em>direction</em> that {@link Verdict} deliberately omits (see
 * ADR 0021, F-6). It is what makes a {@code Verdict.OBSERVED} mean "strength" for one practice and
 * "problem" for another, instead of overloading the verdict value:
 *
 * <ul>
 *   <li>{@code DESIRABLE} — a good habit ("avoid hardcoded secrets", "ship tests with behaviour
 *       changes"). {@code OBSERVED} = strength; {@code NOT_OBSERVED} = the gap to coach.</li>
 *   <li>{@code UNDESIRABLE} — an anti-pattern phrased as the bad behaviour itself. {@code OBSERVED}
 *       = the problem to coach; {@code NOT_OBSERVED} = clean.</li>
 *   <li>{@code MIXED} — context decides (e.g. a pattern that is fine in tests but a smell in
 *       production). The detector's per-finding severity carries the direction case by case.</li>
 * </ul>
 *
 * <p>Every practice in the catalogue today is framed as a desirable habit, so {@code DESIRABLE} is
 * the default; {@code UNDESIRABLE}/{@code MIXED} exist so future anti-pattern detectors need no
 * schema change.
 */
public enum Polarity {
    /** A good habit: {@code OBSERVED} is a strength, {@code NOT_OBSERVED} is the coachable gap. */
    DESIRABLE,
    /** An anti-pattern stated as the bad behaviour: {@code OBSERVED} is the problem, {@code NOT_OBSERVED} is clean. */
    UNDESIRABLE,
    /** Direction depends on context; per-finding severity carries it case by case. */
    MIXED,
}
