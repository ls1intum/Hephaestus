package de.tum.cit.aet.hephaestus.integration.slack.refs;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public interface SlackChannelRepository extends JpaRepository<SlackChannel, Long> {
    Optional<SlackChannel> findByConnectionIdAndChannelId(long connectionId, String channelId);

    List<SlackChannel> findByConnectionId(long connectionId);

    /**
     * Workspace-scoped listing — traverses {@code connection.workspace.id} so the query plan
     * keeps the tenant predicate explicit even when callers only have a workspaceId in hand.
     */
    @Query("SELECT c FROM SlackChannel c WHERE c.connection.workspace.id = :workspaceId")
    List<SlackChannel> findByWorkspaceId(@Param("workspaceId") long workspaceId);

    /**
     * Hard-delete the named channels for a connection. Used by
     * {@code SlackLifecycleListener.onScopeChanged} when Slack reports channel removal.
     *
     * <p>Spring Data derives the SQL from the method name — no {@code @Query}, no risk of
     * a tenant-filter drift. The FK on {@code slack_channel.connection_id} keeps the row
     * set inside one connection, and {@code Connection} is workspace-scoped at the
     * upstream lookup.
     */
    @Transactional
    int deleteByConnectionIdAndChannelIdIn(long connectionId, List<String> channelIds);
}
