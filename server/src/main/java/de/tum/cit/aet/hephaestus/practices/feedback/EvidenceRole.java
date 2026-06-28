package de.tum.cit.aet.hephaestus.practices.feedback;

/**
 * The role a {@code Observation} plays within a synthesized {@code Feedback} unit.
 *
 * <p>A feedback unit is composed of one or more observations. Exactly which observation "leads"
 * the rendered narrative versus which merely reinforces it is a synthesis-time decision,
 * captured here so delivery and dashboards can weight the evidence consistently.
 *
 * <ul>
 *   <li>{@link #PRIMARY} — a problem the unit leads with; a unit may carry several (one per problem).</li>
 *   <li>{@link #SUPPORTING} — a corroborating or strength observation folded into the same unit.</li>
 * </ul>
 */
public enum EvidenceRole {
    /** Anchors the feedback unit's headline message. */
    PRIMARY,
    /** Corroborating evidence folded into the same feedback unit. */
    SUPPORTING,
}
