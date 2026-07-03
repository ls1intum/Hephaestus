package de.tum.cit.aet.hephaestus.integration.slack.domain;

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

    /** Workspace purge: delete every thread aggregate for one workspace. Derived DELETE carries the predicate. */
    long deleteByWorkspaceId(Long workspaceId);

    /** Scoped row count for a workspace — carries the {@code workspace_id} predicate the inspector requires. */
    long countByWorkspaceId(Long workspaceId);
}
