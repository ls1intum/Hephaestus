package de.tum.cit.aet.hephaestus.integration.slack.refs;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public interface SlackMessageRepository extends JpaRepository<SlackMessage, Long> {

    Optional<SlackMessage> findByConnectionIdAndTeamIdAndChannelIdAndTs(
        long connectionId, String teamId, String channelId, String ts);

    /**
     * Newest-first listing within a channel. Hits {@code ix_slack_message_thread}
     * for the O(log n) scan needed by the mentor context loader and the practice-
     * detection precompute scripts.
     */
    List<SlackMessage> findByConnectionIdAndChannelIdOrderByTsDesc(long connectionId, String channelId);

    /** Workspace-scoped newest-first listing for mentor context across all channels of a workspace. */
    @Query(
        "SELECT m FROM SlackMessage m "
            + "WHERE m.connection.workspace.id = :workspaceId "
            + "ORDER BY m.ts DESC"
    )
    List<SlackMessage> findByWorkspaceIdOrderByTsDesc(@Param("workspaceId") long workspaceId);

    /**
     * Hard-purge messages for one channel. Used during channel removal — the channel row
     * is dropped immediately after this call by {@code SlackLifecycleListener}. Derived
     * name (no {@code @Query}) keeps the tenant predicate inherent to {@code connection_id}.
     */
    @Transactional
    int deleteByConnectionIdAndChannelId(long connectionId, String channelId);

    /**
     * Soft-delete via the {@code message_deleted} webhook.
     *
     * <p>Tenant-scoping is twofold: (a) {@code m.connection.workspace.id} is part of the
     * predicate, so a mismatched connection cannot soft-delete a different tenant's row;
     * (b) the {@code deletedAt IS NULL} guard makes the operation idempotent (replayed
     * delivery = 0 rows updated). Returns the count so the handler can distinguish
     * "tombstoned" (1) from "replayed or never ingested" (0) — the latter is not an error.
     */
    @Modifying
    @Transactional
    @Query(
        "UPDATE SlackMessage m SET m.deletedAt = :at "
            + "WHERE m.connection.workspace.id = :workspaceId "
            + "AND m.connection.id = :connectionId "
            + "AND m.channelId = :channelId AND m.ts = :ts "
            + "AND m.deletedAt IS NULL"
    )
    int softDelete(
        @Param("workspaceId") long workspaceId,
        @Param("connectionId") long connectionId,
        @Param("channelId") String channelId,
        @Param("ts") String ts,
        @Param("at") Instant at
    );
}
