package de.tum.cit.aet.hephaestus.practices.feedback;

/**
 * Where one piece of feedback appears, by the provider that shows it: its {@link FeedbackChannel} split where
 * the record names the provider. A read-side fact, derived in SQL by {@link FeedbackRepository#LOCATION} and never
 * stored, so it cannot disagree with the channel, the review that composed the feedback, or the conversation that
 * showed it.
 *
 * <p>{@link #OTHER_WORK} and {@link #NEXT_CONVERSATION} are the channel without a provider. They make the split
 * total: every piece of feedback has exactly one location.
 */
public enum FeedbackLocation {
    /** {@link FeedbackChannel#IN_CONTEXT} on work that the review read from GitHub. */
    GITHUB,
    /** {@link FeedbackChannel#IN_CONTEXT} on work that the review read from GitLab. */
    GITLAB,
    /** {@link FeedbackChannel#IN_CONTEXT} on work at another provider, or at a provider the record does not name. */
    OTHER_WORK,
    /** {@link FeedbackChannel#IN_CHAT}, linked to a turn of a conversation with Heph in the app. */
    HEPH,
    /** {@link FeedbackChannel#IN_CHAT}, linked to a turn of a conversation with Heph in a Slack direct message. */
    SLACK,
    /** {@link FeedbackChannel#IN_CHAT} with no recorded turn: not raised yet, or the turn is gone. */
    NEXT_CONVERSATION,
    /** {@link FeedbackChannel#IN_APP}: the developer's own practice pages. */
    PRACTICE_PAGES,
}
