package de.tum.cit.aet.hephaestus.practices.finding.reaction;

/**
 * Actions a developer can take on an AI-generated practice finding.
 *
 * <p>Designed for research signal, not workflow management:
 * <ul>
 *   <li>{@link #APPLIED} — "I fixed/will fix this" (RQ2: did users act on guidance?)</li>
 *   <li>{@link #DISPUTED} — "The AI is wrong" (RQ1/RQ4: detection quality reaction)</li>
 *   <li>{@link #NOT_APPLICABLE} — "Valid observation but not relevant to my context" (RQ4: scope tuning)</li>
 * </ul>
 *
 * <p>No DISMISSED or ACKNOWLEDGED — non-action is the absence of a reaction row.
 */
public enum FindingReactionAction {
    /** Developer fixed or will fix the issue identified by the finding. */
    APPLIED,
    /** Developer believes the AI assessment is incorrect. Requires an explanation. */
    DISPUTED,
    /** Finding is valid but not relevant to the developer's context. */
    NOT_APPLICABLE,
}
