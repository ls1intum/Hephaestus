package de.tum.cit.aet.hephaestus.integration.slack.domain;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

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

    /** Workspace purge: delete every thread aggregate for one workspace (S2). Derived DELETE carries the predicate. */
    long deleteByWorkspaceId(Long workspaceId);

    /** Scoped row count for a workspace — carries the {@code workspace_id} predicate the inspector requires. */
    long countByWorkspaceId(Long workspaceId);
}
