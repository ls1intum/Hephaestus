package de.tum.cit.aet.hephaestus.agent.job;

import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * A finished job and which of its two feedback-preparation lanes has no record of having run.
 *
 * @param inChatPreparedAt when the conversational lane finished, or {@code null} if it never did
 * @param inAppPreparedAt when the in-app lane finished, or {@code null} if it never did
 */
public record UnpreparedFeedbackLanes(
        UUID agentJobId,
        Long workspaceId,
        @Nullable Instant inChatPreparedAt,
        @Nullable Instant inAppPreparedAt) {
    public boolean inChatPending() {
        return inChatPreparedAt == null;
    }

    public boolean inAppPending() {
        return inAppPreparedAt == null;
    }
}
