package de.tum.cit.aet.hephaestus.integration.slack.domain;

import java.time.Instant;
import org.jspecify.annotations.Nullable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Scoped access to ingested Slack messages. Every finder carries the {@code workspace_id} predicate the tenancy
 * {@code StatementInspector} requires.
 */
public interface SlackMessageRepository extends JpaRepository<SlackMessage, Long> {
    boolean existsByWorkspaceIdAndSlackChannelIdAndSlackTs(Long workspaceId, String slackChannelId, String slackTs);

    /** Bounded-retention sweep: delete every message ingested before {@code cutoff} for one workspace (D10). */
    long deleteByWorkspaceIdAndIngestedAtBefore(Long workspaceId, Instant cutoff);

    /**
     * Idempotent ingest: insert the rendered message and no-op on the unique
     * {@code (workspace_id, slack_channel_id, slack_ts)} conflict (Slack retries un-acked events). INSERTs are
     * exempt from the tenancy predicate check. Returns the number of rows actually inserted (0 on a duplicate).
     */
    @Modifying
    @Query(
        value = """
        INSERT INTO slack_message (workspace_id, slack_team_id, slack_channel_id, slack_ts, slack_thread_ts, author_slack_user_id, text, ingested_at)
        VALUES (:workspaceId, :slackTeamId, :slackChannelId, :slackTs, :slackThreadTs, :authorSlackUserId, :text, now())
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
        @Param("text") @Nullable String text
    );
}
