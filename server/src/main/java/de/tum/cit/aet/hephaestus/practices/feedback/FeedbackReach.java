package de.tum.cit.aet.hephaestus.practices.feedback;

/**
 * Where a workspace's practice feedback may go — the reach axis of the delivery gate.
 *
 * <p><b>One decision per workspace, not per practice.</b> Reach used to be folded into the per-practice
 * loudness ladder, which meant every practice carried its own answer to a question almost no workspace
 * answers differently per practice. At a hundred practices that is a hundred decisions nobody makes, so the
 * shipped default was the only setting anyone ever ran. Reach is a statement about how this workspace uses
 * the system — may it speak on the work, or only in the mentor conversation — and one statement is enough.
 *
 * <p>Reach is ANDed with {@link de.tum.cit.aet.hephaestus.practices.model.PracticeReviewTier}, which answers
 * the orthogonal question of how much autonomy the system has. A practice at {@code OBSERVE} says nothing
 * anywhere no matter how wide the reach; a workspace at {@link #CONVERSATION} never comments on the work no
 * matter how many practices sit at {@code DELIVER}.
 *
 * <p>Each value adds to the one before it, so widening reach never silences a channel that was already
 * speaking.
 */
public enum FeedbackReach {
    /**
     * Only in the recipient's mentor conversation. Nothing is placed on the work itself, so the system is
     * visible to the developer it is about and to nobody else.
     */
    CONVERSATION,

    /** Also on the work itself: pull-request summaries, inline notes, issue comments. */
    ON_THE_WORK;

    /** Width of the {@code practice_feedback_reach} column; every constant name must fit. */
    public static final int MAX_LENGTH = 16;

    /**
     * The reach in force where a workspace has expressed no opinion. {@link #ON_THE_WORK}, because that is
     * what every practice did before reach was a separate setting.
     */
    public static final FeedbackReach DEFAULT = ON_THE_WORK;

    /**
     * Whether feedback may be delivered on {@code channel} at this reach.
     *
     * <p>The single definition point for "may this workspace speak here". Both delivery paths ask this
     * rather than comparing constants themselves, so a new channel is one edit.
     */
    public boolean reaches(FeedbackChannel channel) {
        return switch (channel) {
            case IN_CONTEXT -> this == ON_THE_WORK;
            case CONVERSATION -> true;
            // Declared but unwritten: nothing produces a PROFILE unit, so any other answer would describe a
            // delivery that cannot happen. A producer would put this on the CONVERSATION side.
            case PROFILE -> false;
        };
    }
}
