package de.tum.cit.aet.hephaestus.integration.slack.domain;

import de.tum.cit.aet.hephaestus.integration.slack.domain.SlackMonitoredChannel.ConsentState;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

/**
 * Scoped access to allow-listed Slack channels. Every finder carries the {@code workspace_id} predicate the
 * tenancy {@code StatementInspector} requires.
 */
public interface SlackMonitoredChannelRepository extends JpaRepository<SlackMonitoredChannel, Long> {
    Optional<SlackMonitoredChannel> findByWorkspaceIdAndSlackChannelId(Long workspaceId, String slackChannelId);

    boolean existsByWorkspaceIdAndSlackChannelId(Long workspaceId, String slackChannelId);

    /**
     * The consent lifecycle state of one allow-listed channel, if the row exists. Carries the
     * {@code workspace_id} predicate the tenancy {@code StatementInspector} requires. Read by the ingest
     * gate: a message is only persisted when this is {@link ConsentState#ACTIVE}.
     */
    @Query(
        "SELECT c.consentState FROM SlackMonitoredChannel c " +
            "WHERE c.workspaceId = :workspaceId AND c.slackChannelId = :slackChannelId"
    )
    Optional<ConsentState> findConsentState(
        @Param("workspaceId") Long workspaceId,
        @Param("slackChannelId") String slackChannelId
    );

    /**
     * Data-subject / channel erasure: flip a channel's consent to {@code REVOKED} so ingestion stops
     * immediately. Scoped UPDATE carrying the {@code workspace_id} predicate. Returns the rows affected (0 when
     * the channel was never allow-listed). The stored message history is cleared separately by the caller.
     */
    @Modifying
    @Transactional
    @Query(
        "UPDATE SlackMonitoredChannel c SET c.consentState = " +
            "de.tum.cit.aet.hephaestus.integration.slack.domain.SlackMonitoredChannel.ConsentState.REVOKED " +
            "WHERE c.workspaceId = :workspaceId AND c.slackChannelId = :slackChannelId"
    )
    int revokeConsent(@Param("workspaceId") Long workspaceId, @Param("slackChannelId") String slackChannelId);

    /**
     * Idempotent allow-list registration: create the channel row on first sight (consent {@code PENDING})
     * and no-op on the unique {@code (workspace_id, slack_channel_id)} conflict. INSERTs are exempt from the
     * tenancy predicate check (creating a row cannot leak across workspaces).
     */
    @Modifying
    @Transactional
    @Query(
        value = """
        INSERT INTO slack_monitored_channel (workspace_id, slack_team_id, slack_channel_id, consent_state, created_at)
        VALUES (:workspaceId, :slackTeamId, :slackChannelId, 'PENDING', now())
        ON CONFLICT (workspace_id, slack_channel_id) DO NOTHING
        """,
        nativeQuery = true
    )
    void insertIfAbsent(
        @Param("workspaceId") long workspaceId,
        @Param("slackTeamId") String slackTeamId,
        @Param("slackChannelId") String slackChannelId
    );

    /** Workspace purge: delete every allow-list row for one workspace. Derived DELETE carries the predicate. */
    long deleteByWorkspaceId(Long workspaceId);

    /** Scoped row count for a workspace — carries the {@code workspace_id} predicate the inspector requires. */
    long countByWorkspaceId(Long workspaceId);
}
