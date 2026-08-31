package de.tum.cit.aet.hephaestus.agent.handler;

import de.tum.cit.aet.hephaestus.agent.handler.spi.JobSubmissionRequest;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Submission request for {@code CONVERSATION_REVIEW} jobs:
 * one settled Slack thread, identified by {@code (channelId, threadTs)}, and the single participant the
 * observations are filed against.
 *
 * @param slackThreadId the {@code slack_thread} aggregate id (the delivery artifactId)
 * @param slackChannelName the channel name captured when the review was submitted
 * @param slackThreadTs the thread root {@code ts}
 * @param aboutUserId the resolved workspace member id whose turns the observations are about (the DM recipient)
 * @param lastTs the thread's newest message {@code ts} — the disposable freshness segment
 */
public record ConversationReviewSubmissionRequest(
        long slackThreadId,
        String slackChannelId,
        @Nullable String slackChannelName,
        String slackThreadTs,
        long aboutUserId,
        String lastTs)
        implements JobSubmissionRequest {
    public ConversationReviewSubmissionRequest {
        Objects.requireNonNull(slackChannelId, "slackChannelId must not be null");
        Objects.requireNonNull(slackThreadTs, "slackThreadTs must not be null");
        Objects.requireNonNull(lastTs, "lastTs must not be null");
        slackChannelName = slackChannelName == null || slackChannelName.isBlank() ? null : slackChannelName;
        if (slackChannelId.isBlank()) {
            throw new IllegalArgumentException("slackChannelId must not be blank");
        }
        if (slackThreadTs.isBlank()) {
            throw new IllegalArgumentException("slackThreadTs must not be blank");
        }
        if (slackThreadId <= 0) {
            throw new IllegalArgumentException("slackThreadId must be positive, got " + slackThreadId);
        }
        if (aboutUserId <= 0) {
            throw new IllegalArgumentException("aboutUserId must be positive, got " + aboutUserId);
        }
    }
}
