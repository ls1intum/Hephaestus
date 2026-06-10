package de.tum.cit.aet.hephaestus.practices.feedback;

/**
 * The surfaces through which composed feedback reaches a contributor or facilitator, each at a
 * distinct Hattie &amp; Timperley feedback level. Carried on both {@code FeedbackDelivery} (push
 * channels emit a stored snapshot) and {@code FeedbackInteraction} (every channel logs engagement).
 *
 * <p>The Hattie level is a stable property of the channel and is therefore NOT stored as a column —
 * derive it from the channel. New channels (e.g. email digest, notification inbox) add a value here,
 * never a new table.
 */
public enum FeedbackChannel {
    /** Findings shown inside an open PR/MR as it moves draft→merge. PUSH; task level. */
    IN_CONTEXT_PR,
    /** A view the contributor consults on their own initiative, findings under active goals. PULL; process level. */
    REFLECTION_DASHBOARD,
    /** Two-way chat that integrates findings + project state as context. Dialogue; self-regulation level. */
    CONVERSATIONAL_MENTOR,
    /** A view for tutors/coaches, aggregated by practice goal across contributors. PULL; decision support. */
    FACILITATOR_DASHBOARD,
}
