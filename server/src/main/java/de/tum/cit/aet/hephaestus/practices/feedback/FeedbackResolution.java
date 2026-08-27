package de.tum.cit.aet.hephaestus.practices.feedback;

/** What the recipient decided to do with a delivered piece of feedback. */
public enum FeedbackResolution {
    /** The recipient acted, or intends to act, on the guidance. */
    ADDRESSED,
    /** The recipient rejects the observation with a reasoned explanation. */
    DISPUTED,
    /** The observation may be sound but does not apply in this context. */
    NOT_APPLICABLE,
}
