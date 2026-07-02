package de.tum.cit.aet.hephaestus.agent.mentor.chat;

import de.tum.cit.aet.hephaestus.mentor.ThreadSurface;
import java.util.UUID;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * One mentor turn, transport-neutral. Carries only what a turn needs — the developer is resolved from the
 * security context inside the turn body, not from here. {@code surface} lets the shared turn body adapt
 * output style per client (web vs Slack DM) without any transport branching.
 */
public record MentorTurnRequest(
    long workspaceId,
    @NonNull UUID threadId,
    @NonNull String userMessage,
    @Nullable UUID clientUserMessageId,
    @NonNull ThreadSurface surface
) {
    /** A turn from the webapp SSE surface. */
    public static MentorTurnRequest web(
        long workspaceId,
        UUID threadId,
        String userMessage,
        @Nullable UUID clientUserMessageId
    ) {
        return new MentorTurnRequest(workspaceId, threadId, userMessage, clientUserMessageId, ThreadSurface.WEB);
    }

    /** A turn from a Slack DM surface. Lets callers construct one without naming the {@code mentor}-module enum. */
    public static MentorTurnRequest slackDm(long workspaceId, UUID threadId, String userMessage) {
        return new MentorTurnRequest(workspaceId, threadId, userMessage, null, ThreadSurface.SLACK_DM);
    }
}
