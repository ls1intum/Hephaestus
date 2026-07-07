package de.tum.cit.aet.hephaestus.integration.slack.domain;

/**
 * The binary verdict a member gives a mentor turn via the feedback buttons attached to the reply.
 *
 * <p>Deliberately two-valued: a thumb is a lightweight satisfaction signal about the turn, recorded as a
 * {@code mentor_turn_rating} row.
 */
public enum TurnRating {
    HELPFUL,
    UNHELPFUL,
}
