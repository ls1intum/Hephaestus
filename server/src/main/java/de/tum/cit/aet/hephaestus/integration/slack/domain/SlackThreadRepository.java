package de.tum.cit.aet.hephaestus.integration.slack.domain;

import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

/**
 * Scoped access to Slack thread aggregates. Every finder carries the {@code workspace_id} predicate the tenancy
 * {@code StatementInspector} requires.
 */
public interface SlackThreadRepository extends JpaRepository<SlackThread, Long> {
    Optional<SlackThread> findByWorkspaceIdAndSlackChannelIdAndSlackThreadTs(
        Long workspaceId,
        String slackChannelId,
        String slackThreadTs
    );

    /**
     * Idempotent thread-aggregate upsert on a freshly ingested message. Creates the
     * {@code (workspace_id, slack_channel_id, slack_thread_ts)} row on first sight and, on the unique conflict,
     * advances the {@code first_ts}/{@code last_ts} window (Slack {@code ts} strings sort lexicographically —
     * fixed {@code <10-digit>.<6-digit>} format), bumps {@code message_count}, and unions the author's resolved
     * member id into {@code participant_member_ids} (the GIN-indexed {@code bigint[]} the participant firewall
     * matches with {@code = ANY(...)}). {@code participant_member_ids} is deliberately unmapped on the entity
     * (raw {@code bigint[]}), so this write is a native {@code @Modifying} statement rather than an entity save.
     *
     * <p>The caller passes {@code slackThreadTs := thread_ts} for a reply and {@code := ts} for a thread root, so
     * a root and its replies collapse onto one aggregate row. {@code authorMemberId} is nullable (an unlinked
     * Slack author stamps no member id); the union is a no-op in that case. INSERTs/ON CONFLICT updates carry the
     * explicit {@code workspace_id} in the row, so no tenant leak is possible.
     */
    @Modifying
    @Transactional
    @Query(
        value = """
        INSERT INTO slack_thread (workspace_id, slack_channel_id, slack_thread_ts, first_ts, last_ts, message_count, participant_member_ids, created_at)
        VALUES (
            :workspaceId, :slackChannelId, :slackThreadTs, :slackTs, :slackTs, 1,
            CASE WHEN :authorMemberId IS NULL THEN '{}'::bigint[] ELSE ARRAY[CAST(:authorMemberId AS bigint)] END,
            now()
        )
        ON CONFLICT (workspace_id, slack_channel_id, slack_thread_ts) DO UPDATE SET
            first_ts = CASE
                WHEN slack_thread.first_ts IS NULL OR EXCLUDED.first_ts < slack_thread.first_ts
                THEN EXCLUDED.first_ts ELSE slack_thread.first_ts END,
            last_ts = CASE
                WHEN slack_thread.last_ts IS NULL OR EXCLUDED.last_ts > slack_thread.last_ts
                THEN EXCLUDED.last_ts ELSE slack_thread.last_ts END,
            message_count = slack_thread.message_count + 1,
            participant_member_ids = CASE
                WHEN :authorMemberId IS NULL OR CAST(:authorMemberId AS bigint) = ANY(slack_thread.participant_member_ids)
                THEN slack_thread.participant_member_ids
                ELSE array_append(slack_thread.participant_member_ids, CAST(:authorMemberId AS bigint)) END
        """,
        nativeQuery = true
    )
    void upsertOnMessage(
        @Param("workspaceId") long workspaceId,
        @Param("slackChannelId") String slackChannelId,
        @Param("slackThreadTs") String slackThreadTs,
        @Param("slackTs") String slackTs,
        @Param("authorMemberId") @Nullable Long authorMemberId
    );

    /**
     * The ids of every thread aggregate on one channel — collected on channel erasure so the derived
     * {@code CONVERSATION_THREAD} observations/feedback (keyed by these {@code slack_thread} ids as
     * {@code artifact_id}) can be hard-deleted through the practices erasure port before the aggregates themselves
     * are dropped. Carries the {@code workspace_id} predicate the tenancy inspector requires.
     */
    @Query("SELECT t.id FROM SlackThread t WHERE t.workspaceId = :workspaceId AND t.slackChannelId = :slackChannelId")
    List<Long> findIdsByWorkspaceIdAndSlackChannelId(
        @Param("workspaceId") Long workspaceId,
        @Param("slackChannelId") String slackChannelId
    );

    /** Workspace purge: delete every thread aggregate for one workspace. Derived DELETE carries the predicate. */
    long deleteByWorkspaceId(Long workspaceId);

    /**
     * Channel erasure: delete every thread aggregate of one channel promptly when its consent is withdrawn (the
     * aggregates hold the {@code participant_member_ids} personal data and are the artifact the derived CONVERSATION
     * feedback points at). Derived DELETE carries the {@code workspace_id} predicate the tenancy inspector requires;
     * idempotent (returns 0 when the channel had no threads).
     */
    long deleteByWorkspaceIdAndSlackChannelId(Long workspaceId, String slackChannelId);

    /**
     * Retention sweep: the ids of every thread aggregate for a workspace that has gone cold — its most recent
     * activity ({@code last_ts}, the newest message in the thread) is strictly older than {@code cutoffTs}.
     * Because {@code last_ts} is the maximum message {@code ts} in the thread, "last_ts older than the cutoff" is
     * exactly "every message in the thread is older than the cutoff", so the whole thread (raw messages, the
     * {@code participant_member_ids} aggregate, and the derived CONVERSATION feedback) can be erased together.
     *
     * <p>{@code cutoffTs} is the retention cutoff rendered as a Slack {@code ts} string
     * ({@code <10-digit-epoch-seconds>.000000}); the comparison is lexicographic, which equals numeric ordering for
     * the fixed {@code <10-digit>.<6-digit>} Slack {@code ts} format (the same invariant {@link #upsertOnMessage}
     * relies on). {@code last_ts} is always populated on ingest, so a NULL guard is unnecessary. Carries the
     * {@code workspace_id} predicate the tenancy inspector requires.
     */
    @Query(
        "SELECT t.id FROM SlackThread t WHERE t.workspaceId = :workspaceId AND t.lastTs IS NOT NULL AND t.lastTs < :cutoffTs"
    )
    List<Long> findAgedThreadIds(@Param("workspaceId") Long workspaceId, @Param("cutoffTs") String cutoffTs);

    /**
     * Retention sweep: drop a set of aged thread aggregates for a workspace after their derived CONVERSATION feedback
     * has already been erased through the practices port. Carries the {@code workspace_id} predicate the tenancy
     * inspector requires; idempotent (0 when the id set is empty / already gone). Callers guard an empty collection.
     */
    @Modifying
    @Transactional
    @Query("DELETE FROM SlackThread t WHERE t.workspaceId = :workspaceId AND t.id IN :ids")
    int deleteByWorkspaceIdAndIdIn(
        @Param("workspaceId") Long workspaceId,
        @Param("ids") java.util.Collection<Long> ids
    );

    /**
     * Person erasure (opt-out / account hard-delete): drop one member's id out of every thread's
     * {@code participant_member_ids} for a workspace via {@code array_remove}. The append-only participant array
     * otherwise keeps a person's id (and, on id reuse, their thread visibility) after they leave — this prunes it.
     * The {@code :memberId = ANY(participant_member_ids)} guard narrows the write set to rows that actually contain
     * the member (the GIN index on the array serves it), so unaffected threads are not rewritten. Native (the
     * {@code participant_member_ids} {@code bigint[]} is unmapped on the entity) and workspace-scoped. Idempotent
     * (0 when no thread references the member).
     *
     * @return the number of thread aggregates pruned
     */
    @Modifying
    @Transactional
    @Query(
        value = "UPDATE slack_thread SET participant_member_ids = array_remove(participant_member_ids, :memberId) " +
            "WHERE workspace_id = :workspaceId AND :memberId = ANY(participant_member_ids)",
        nativeQuery = true
    )
    int pruneParticipant(@Param("workspaceId") long workspaceId, @Param("memberId") long memberId);

    /** Scoped row count for a workspace — carries the {@code workspace_id} predicate the inspector requires. */
    long countByWorkspaceId(Long workspaceId);
}
