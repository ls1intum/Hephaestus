package de.tum.cit.aet.hephaestus.agent.handler;

import de.tum.cit.aet.hephaestus.agent.handler.spi.JobSubmissionRequest;
import java.util.Objects;

/**
 * Submission request for {@link de.tum.cit.aet.hephaestus.agent.AgentJobType#CONVERSATION_REVIEW} jobs.
 *
 * <p>Identifies one settled Slack thread and the single participant the findings are filed against. There is no
 * repository / diff / head SHA — the thread is identified by {@code (channelId, threadTs)} and the freshness
 * segment of the idempotency key is the thread's newest {@code ts} ({@code lastTs}), which plays the role the
 * head SHA plays for a PR: a thread that grew re-reviews, while {@code extractCooldownKeyPrefix} strips it so the
 * built-in cooldown scopes on the thread + subject alone (a late reply with a new {@code lastTs} does not
 * re-fire).
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
