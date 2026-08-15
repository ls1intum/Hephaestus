package de.tum.cit.aet.hephaestus.practices.feedback;

/**
 * Where a workspace's practice feedback may go — the reach axis of the delivery gate. One decision per
 * workspace, not per practice.
 *
 * <p>ANDed with {@link de.tum.cit.aet.hephaestus.practices.model.PracticeReviewTier}, the orthogonal axis
 * of autonomy: a practice at {@code PROPOSE} says nothing no matter how wide the reach, and a workspace at
 * {@link #CONVERSATION} never comments on the work no matter how many practices sit at {@code DELIVER}.
 *
 * <p>Each value adds to the one before it, so widening reach never silences a channel already speaking.
 */
public enum FeedbackReach {
    /** Only in the recipient's mentor conversation — nothing is placed on the work itself. */
    CONVERSATION,

    /** Also on the work itself: pull-request summaries, inline notes, issue comments. */
    ON_THE_WORK;

    /** Width of the {@code practice_feedback_reach} column; every constant name must fit. */
    public static final int MAX_LENGTH = 16;

    /** {@link #ON_THE_WORK}, because that is what every practice did before reach was a separate setting. */
    public static final FeedbackReach DEFAULT = ON_THE_WORK;

    public boolean reaches(FeedbackChannel channel) {
        return switch (channel) {
            case IN_CONTEXT -> this == ON_THE_WORK;
            case CONVERSATION -> true;
            // Declared but unwritten: nothing produces a PROFILE unit yet.
            case PROFILE -> false;
        };
    }
}
