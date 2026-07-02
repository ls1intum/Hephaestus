package de.tum.cit.aet.hephaestus.integration.slack.domain;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Scoped access to allow-listed Slack channels. Every finder carries the {@code workspace_id} predicate the
 * tenancy {@code StatementInspector} requires.
 */
public interface SlackMonitoredChannelRepository extends JpaRepository<SlackMonitoredChannel, Long> {
    Optional<SlackMonitoredChannel> findByWorkspaceIdAndSlackChannelId(Long workspaceId, String slackChannelId);

    boolean existsByWorkspaceIdAndSlackChannelId(Long workspaceId, String slackChannelId);

    /**
     * Idempotent allow-list registration: create the channel row on first sight (consent {@code PENDING},
     * backfill {@code NONE}) and no-op on the unique {@code (workspace_id, slack_channel_id)} conflict. INSERTs
     * are exempt from the tenancy predicate check (creating a row cannot leak across workspaces).
     */
    @Modifying
    @Query(
        value = """
        INSERT INTO slack_monitored_channel (workspace_id, slack_team_id, slack_channel_id, consent_state, backfill_state, created_at)
        VALUES (:workspaceId, :slackTeamId, :slackChannelId, 'PENDING', 'NONE', now())
        ON CONFLICT (workspace_id, slack_channel_id) DO NOTHING
        """,
        nativeQuery = true
    )
    void insertIfAbsent(
        @Param("workspaceId") long workspaceId,
        @Param("slackTeamId") String slackTeamId,
        @Param("slackChannelId") String slackChannelId
    );
}
