package de.tum.cit.aet.hephaestus.practices.feedback;

/**
 * The role a {@code PracticeFinding} plays within a synthesized {@code Feedback} unit.
 *
 * <p>A feedback unit is composed of one or more findings. Exactly which finding "leads"
 * the rendered narrative versus which merely reinforces it is a synthesis-time decision,
 * captured here so delivery and dashboards can weight the evidence consistently.
 *
 * <ul>
 *   <li>{@link #PRIMARY} — the finding that anchors the feedback's headline message.</li>
 *   <li>{@link #SUPPORTING} — corroborating evidence folded into the same unit.</li>
 * </ul>
 */
public enum FeedbackFindingDisplayRole {
    /** Anchors the feedback unit's headline message. */
    PRIMARY,
    /** Corroborating evidence folded into the same feedback unit. */
    SUPPORTING,
}
