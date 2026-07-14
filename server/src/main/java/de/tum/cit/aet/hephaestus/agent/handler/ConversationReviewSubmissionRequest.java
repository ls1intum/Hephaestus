package de.tum.cit.aet.hephaestus.agent.handler;

import de.tum.cit.aet.hephaestus.agent.handler.spi.JobSubmissionRequest;
import java.util.Objects;

/**
 * Submission request for {@link de.tum.cit.aet.hephaestus.agent.AgentJobType#CONVERSATION_REVIEW} jobs:
 * one settled Slack thread, identified by {@code (channelId, threadTs)}, and the single participant the
 * findings are filed against. Idempotency/cooldown semantics of {@code lastTs} are documented at the key
 * construction in {@link ConversationReviewHandler#createSubmission}.
 *
 * @param slackThreadId the {@code slack_thread} aggregate id (the delivery artifactId)
 * @param slackChannelId the Slack channel id
 * @param slackThreadTs the thread root {@code ts}
 * @param aboutUserId the resolved workspace member id whose turns the findings are about (the DM recipient)
 * @param lastTs the thread's newest message {@code ts} — the disposable freshness segment
 */
public record ConversationReviewSubmissionRequest(
    long slackThreadId,
    String slackChannelId,
    String slackThreadTs,
    long aboutUserId,
    String lastTs
) implements JobSubmissionRequest {
    public ConversationReviewSubmissionRequest {
        Objects.requireNonNull(slackChannelId, "slackChannelId must not be null");
        Objects.requireNonNull(slackThreadTs, "slackThreadTs must not be null");
        Objects.requireNonNull(lastTs, "lastTs must not be null");
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
