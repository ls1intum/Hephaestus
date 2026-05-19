package de.tum.in.www1.hephaestus.practices.finding.feedback;

/**
 * Actions a contributor can take on an AI-generated practice finding.
 *
 * <p>Designed for research signal, not workflow management:
 * <ul>
 *   <li>{@link #APPLIED} — "I fixed/will fix this" (RQ2: did users act on guidance?)</li>
 *   <li>{@link #DISPUTED} — "The AI is wrong" (RQ1/RQ4: detection quality feedback)</li>
 *   <li>{@link #NOT_APPLICABLE} — "Valid observation but not relevant to my context" (RQ4: scope tuning)</li>
 * </ul>
 *
 * <p>No DISMISSED or ACKNOWLEDGED — non-action is the absence of a feedback row.
 */
public enum FindingFeedbackAction {
    /** Contributor fixed or will fix the issue identified by the finding. */
    APPLIED,
    /** Contributor believes the AI assessment is incorrect. Requires an explanation. */
    DISPUTED,
    /** Finding is valid but not relevant to the contributor's context. */
    NOT_APPLICABLE,
}
