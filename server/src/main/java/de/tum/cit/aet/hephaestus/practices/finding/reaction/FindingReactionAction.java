package de.tum.cit.aet.hephaestus.practices.finding.reaction;

/**
 * A developer's reaction to an AI-generated practice finding. Captured as signal, not workflow.
 *
 * <p>The value set partitions into two orthogonal axes: a RESPONSE axis — what the developer DID with the
 * feedback — and a VALIDITY axis — whether the finding applied at all. Conflating them contaminates any
 * uptake metric, so readers MUST keep {@link #NOT_APPLICABLE} out of response/uptake ratios (enforced in
 * {@code FindingReactionEngagementDTO}).
 *
 * <ul>
 *   <li>RESPONSE · {@link #ENACTED} — the developer acted on the guidance ("I fixed / will fix this").
 *       Named for the recipience ACT, not its outcome: a later run may still find the locus NOT_OBSERVED,
 *       which the suppression filter ESCALATES rather than treating as done — so a value named "applied"
 *       (=outcome) would contradict that behaviour.</li>
 *   <li>RESPONSE · {@link #DISPUTED} — a REASONED rejection of the finding; the required explanation IS the
 *       evaluative judgement, not mere disagreement.</li>
 *   <li>VALIDITY · {@link #NOT_APPLICABLE} — the finding is sound but out of scope for this context; a
 *       relevance/scope signal, NOT an uptake outcome.</li>
 * </ul>
 *
 * <p>No {@code DISMISSED} (rejection-without-reason) or {@code ACKNOWLEDGED} ("saw it"): neither is a
 * volitional recipience act the current UI elicits, so under the "non-action = no reaction row" design they
 * would fabricate data rather than measure it. "Seen" belongs to {@code Feedback} delivery telemetry.
 */
public enum FindingReactionAction {
    /** RESPONSE: the developer acted on the guidance. The act, not the outcome — a recurrence is escalated. */
    ENACTED,
    /** RESPONSE: a reasoned rejection of the finding. Requires an explanation (the evaluative judgement). */
    DISPUTED,
    /** VALIDITY: the finding is sound but out of scope here. A relevance signal — never an uptake count. */
    NOT_APPLICABLE,
}
