package de.tum.cit.aet.hephaestus.practices.feedback.interaction;

/**
 * The kind of object a {@link FeedbackInteraction} is about (the "object" of actor–verb–object).
 * The concrete id is held as a string {@code target_ref} because targets are heterogeneous
 * (finding/delivery = UUID, practice goal = long, session = opaque id).
 */
public enum FeedbackInteractionTarget {
    FINDING,
    DELIVERY,
    PRACTICE_GOAL,
    DASHBOARD_SESSION,
    MENTOR_SESSION,
}
