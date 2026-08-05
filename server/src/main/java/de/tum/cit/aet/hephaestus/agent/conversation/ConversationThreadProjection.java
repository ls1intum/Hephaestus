package de.tum.cit.aet.hephaestus.agent.conversation;

import java.time.Instant;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.node.ObjectNode;

/**
 * Agent-owned SPI: projects the Slack channel-ingest substrate into the thread-grouped conversation payloads the
 * agent materialises into the sandbox context. Implemented by {@code integration.slack} (the owner of the
 * {@code slack_thread}/{@code slack_message}/{@code slack_monitored_channel} tables); the agent content sources
 * ({@code SlackConversationContentSource}, {@code ConversationThreadContentSource}) consume it and never touch
 * the Slack schema.
 *
 * <p>The implementation owns the privacy invariants: the participant firewall on the developer view, the
 * {@code consent_state = 'ACTIVE'} gate applied on the raw message read (so a channel paused/revoked between
 * enqueue and execution yields an empty payload), the non-tombstoned filter, and the untrusted-content
 * quarantine envelope. Both methods are pure reads.
 */
public interface ConversationThreadProjection {
    /**
     * The thread-grouped conversation payload for one audience within one workspace — the Slack threads the
     * requesting developer participated in, from channels whose consent is {@code ACTIVE}, non-tombstoned
     * messages only. Wrapped in the untrusted-content quarantine envelope.
     *
     * @param workspaceId     the workspace to scope every query to
     * @param developerId     the mentor-chat requester's SCM user id — the participant-firewall audience
     */
    ObjectNode buildPayload(long workspaceId, long developerId);

    /**
     * The ordered-turns payload for a SINGLE settled thread (conversation detection). No participant firewall —
     * the detection job judges the thread as a work artifact — but the same {@code consent_state = 'ACTIVE'} gate
     * and untrusted-content quarantine envelope apply, so a non-ACTIVE channel yields an empty payload.
     *
     * @param workspaceId the workspace to scope every query to
     * @param channelId   the thread's Slack channel id
     * @param threadTs    the thread root {@code ts} (aggregate key)
     */
    ObjectNode buildThreadPayload(long workspaceId, String channelId, String threadTs);

    /**
     * Whether the thread exists and its channel's consent is {@code ACTIVE}.
     *
     * <p>{@link #buildThreadPayload} answers a withheld channel, a tombstoned thread, and a thread
     * where nobody spoke with the same empty payload. Only the first two are absences of permission
     * or of the artifact; reporting them as an empty conversation would have the model judge a
     * developer's communication on evidence it was never allowed to see.
     */
    default ThreadReadability threadReadability(long workspaceId, String channelId, String threadTs) {
        return ThreadReadability.READABLE;
    }

    enum ThreadReadability {
        READABLE,
        /** The channel's consent is not ACTIVE, so its messages must not be read. */
        CONSENT_NOT_ACTIVE,
        /** No such thread in this workspace. */
        NOT_FOUND,
    }

    default @Nullable Instant sourceEffectiveAt(String sourceEventId) {
        return null;
    }
}
