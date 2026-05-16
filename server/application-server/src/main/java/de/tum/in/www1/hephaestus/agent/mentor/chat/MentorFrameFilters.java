package de.tum.in.www1.hephaestus.agent.mentor.chat;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * JSON-RPC-aware {@link Predicate} factories for use with
 * {@code AttachedSandbox.subscribe(Cursor, Predicate, Consumer)}.
 *
 * <p>The mentor sandbox is shared by {@code (userId, workspaceId)}: a second chat tab in the
 * same workspace subscribes to the SAME frame stream. The bound thread filter drops frames
 * whose JSON-RPC {@code params.threadId} does not match the subscriber's thread; without it,
 * tab-A's translator would observe tab-B's text deltas and ship them down tab-A's wire.
 *
 * <p><b>Broadcast contract:</b> frames without a {@code params.threadId} (or with a null one)
 * are server notifications — {@code runner_ready}, ring metadata, server status. Filters MUST
 * pass them through unless deliberately suppressing them. {@code forThread(null)} accepts all
 * frames; this is the test-only path used by legacy unit tests that pre-date multi-session.
 */
final class MentorFrameFilters {

    private MentorFrameFilters() {}

    /** Returns a predicate that passes broadcasts + frames matching {@code threadId}. */
    static Predicate<JsonNode> forThread(UUID threadId) {
        if (threadId == null) {
            return frame -> true;
        }
        String expected = threadId.toString();
        return frame -> {
            // The mentor runner uses two top-level shapes that ride the sandbox frame stream:
            //   • Notifications:  { method, params: { threadId?, event } }
            //   • Responses:      { id, result | error }
            // Responses have no threadId — they correlate to a pending call already routed by id
            // inside MentorRunnerClient, so they MUST pass through every client's filter.
            if (!frame.isObject()) {
                return true;
            }
            JsonNode params = frame.get("params");
            if (params == null || !params.isObject()) {
                return true; // response / non-routed frame
            }
            JsonNode threadIdNode = params.get("threadId");
            if (threadIdNode == null || threadIdNode.isNull()) {
                return true; // broadcast notification (runner_ready, etc.)
            }
            return Objects.equals(threadIdNode.asText(), expected);
        };
    }
}
