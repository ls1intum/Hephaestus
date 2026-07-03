package de.tum.cit.aet.hephaestus.integration.slack.domain;

/**
 * The binary verdict a member gives a mentor turn via the feedback buttons attached to the reply (S5).
 *
 * <p>Deliberately two-valued: a thumb is a lightweight satisfaction signal about the turn, distinct from the
 * three-way {@code ReactionAction} (ADDRESSED / NOT_APPLICABLE / DISPUTED) that records a developer's uptake of a
 * specific piece of feedback. A thumb never writes a Reaction.
 */
public enum TurnRating {
    HELPFUL,
    UNHELPFUL,
}
