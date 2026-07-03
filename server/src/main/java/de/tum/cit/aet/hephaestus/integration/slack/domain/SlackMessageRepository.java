package de.tum.cit.aet.hephaestus.integration.slack.domain;

import java.time.Instant;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

/**
 * Scoped access to ingested Slack messages. Every finder carries the {@code workspace_id} predicate the tenancy
 * {@code StatementInspector} requires.
 */
public interface SlackMessageRepository extends JpaRepository<SlackMessage, Long> {
    boolean existsByWorkspaceIdAndSlackChannelIdAndSlackTs(Long workspaceId, String slackChannelId, String slackTs);

    /** Bounded-retention sweep: delete every message ingested before {@code cutoff} for one workspace (D10). */
    long deleteByWorkspaceIdAndIngestedAtBefore(Long workspaceId, Instant cutoff);

    /** Workspace purge: delete every ingested message for one workspace (S2). Derived DELETE carries the predicate. */
    long deleteByWorkspaceId(Long workspaceId);

    /** Scoped row count for a workspace — carries the {@code workspace_id} predicate the inspector requires. */
    long countByWorkspaceId(Long workspaceId);

    /**
     * Retention-sweep fan-out: every workspace that currently has at least one ingested message. Native +
     * unscoped by design (the {@link de.tum.cit.aet.hephaestus.integration.slack.retention.SlackRetentionSweeper}
     * runs {@code @WorkspaceAgnostic}, so the tenancy {@code StatementInspector} treats this as exempt). Callers
     * outside a bypass scope will trip the inspector — that is intentional.
     */
    @Query(value = "SELECT DISTINCT workspace_id FROM slack_message", nativeQuery = true)
    List<Long> findDistinctWorkspaceIds();

    /**
     * Idempotent ingest: insert the rendered message and no-op on the unique
     * {@code (workspace_id, slack_channel_id, slack_ts)} conflict (Slack retries un-acked events). INSERTs are
     * exempt from the tenancy predicate check. Returns the number of rows actually inserted (0 on a duplicate).
     */
    @Modifying
    @Query(
        value = """
        INSERT INTO slack_message (workspace_id, slack_team_id, slack_channel_id, slack_ts, slack_thread_ts, author_slack_user_id, author_member_id, text, ingested_at)
        VALUES (:workspaceId, :slackTeamId, :slackChannelId, :slackTs, :slackThreadTs, :authorSlackUserId, :authorMemberId, :text, now())
        ON CONFLICT (workspace_id, slack_channel_id, slack_ts) DO NOTHING
        """,
        nativeQuery = true
    )
    int insertIfAbsent(
        @Param("workspaceId") long workspaceId,
        @Param("slackTeamId") String slackTeamId,
        @Param("slackChannelId") String slackChannelId,
        @Param("slackTs") String slackTs,
        @Param("slackThreadTs") @Nullable String slackThreadTs,
        @Param("authorSlackUserId") @Nullable String authorSlackUserId,
        @Param("authorMemberId") @Nullable Long authorMemberId,
        @Param("text") @Nullable String text
    );

    /**
     * Slack {@code message_deleted} tombstone (GDPR Art. 17): stamp {@code deleted_at} and null the stored
     * {@code text} so a deleted message's content no longer lingers or surfaces to the mentor. Scoped JPQL UPDATE
     * carrying the {@code workspace_id} predicate. Returns rows affected (0 when the message was never ingested).
     */
    @Modifying
    @Transactional
    @Query(
        "UPDATE SlackMessage m SET m.deletedAt = CURRENT_TIMESTAMP, m.text = NULL " +
            "WHERE m.workspaceId = :workspaceId AND m.slackChannelId = :slackChannelId AND m.slackTs = :slackTs"
    )
    int tombstone(
        @Param("workspaceId") Long workspaceId,
        @Param("slackChannelId") String slackChannelId,
        @Param("slackTs") String slackTs
    );

    /**
     * Slack {@code message_changed}: replace the rendered {@code text} with the edited body and stamp
     * {@code edited_at}. Scoped JPQL UPDATE carrying the {@code workspace_id} predicate. Never resurrects a
     * tombstoned row ({@code deleted_at IS NULL} guard). Returns rows affected.
     */
    @Modifying
    @Transactional
    @Query(
        "UPDATE SlackMessage m SET m.text = :text, m.editedAt = CURRENT_TIMESTAMP " +
            "WHERE m.workspaceId = :workspaceId AND m.slackChannelId = :slackChannelId AND m.slackTs = :slackTs " +
            "AND m.deletedAt IS NULL"
    )
    int applyEdit(
        @Param("workspaceId") Long workspaceId,
        @Param("slackChannelId") String slackChannelId,
        @Param("slackTs") String slackTs,
        @Param("text") @Nullable String text
    );
}
