package de.tum.cit.aet.hephaestus.agent.conversation;

import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Agent-owned SPI: the settled-thread candidate scan + turn counts + review watermark advance over the Slack
 * channel-ingest substrate. Implemented by {@code integration.slack} (which owns {@code slack_thread}/
 * {@code slack_message}/{@code slack_monitored_channel}); the agent {@code ConversationThreadTriggerScheduler}
 * consumes it to enqueue {@code CONVERSATION_REVIEW} jobs, and never reads the Slack schema.
 *
 * <p>Every method is workspace-pinned by the caller-supplied {@code workspaceId} except {@link #settledCandidates}
 * which is an inherently cross-workspace sweep (each returned candidate carries its own workspace id). Only
 * threads on an {@code ACTIVE} channel are ever surfaced.
 */
public interface ConversationCandidateSource {
    /**
     * Threads on an {@code ACTIVE} channel that have grown past their review watermark and have at least
     * {@code minMessageCount} messages, oldest {@code last_ts} first. Cross-workspace; each candidate carries its
     * own {@code workspaceId}. Gate evaluation (quiescence/depth/growth) and enqueue are the caller's job.
     */
    List<ConversationThreadCandidate> settledCandidates(int minMessageCount);

    /** Count of non-tombstoned turns in the thread (root + replies), workspace-pinned. */
    long liveTurnCount(long workspaceId, String channelId, String threadTs);

    /**
     * Count of non-tombstoned turns with {@code slack_ts} strictly greater than {@code watermark} (lexicographic;
     * a null watermark counts everything), workspace-pinned.
     */
    long liveTurnCountSince(long workspaceId, String channelId, String threadTs, @Nullable String watermark);

    /** Advance the thread's {@code last_reviewed_ts} watermark to {@code lastTs}, workspace-pinned. */
    void markReviewed(long workspaceId, long threadId, String lastTs);
}
