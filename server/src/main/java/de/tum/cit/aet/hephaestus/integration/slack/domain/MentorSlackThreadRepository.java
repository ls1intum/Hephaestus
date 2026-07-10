package de.tum.cit.aet.hephaestus.integration.slack.domain;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Scoped access to the Slack-DM → mentor-thread mapping. Every finder carries the {@code workspace_id} predicate
 * the tenancy {@code StatementInspector} requires.
 */
public interface MentorSlackThreadRepository extends JpaRepository<MentorSlackThread, UUID> {
    Optional<MentorSlackThread> findByWorkspaceIdAndSlackChannelIdAndSlackThreadTs(
        Long workspaceId,
        String slackChannelId,
        String slackThreadTs
    );

    /** Workspace purge: delete every DM→mentor-thread mapping for one workspace. */
    long deleteByWorkspaceId(Long workspaceId);

    /** Scoped row count for a workspace. */
    long countByWorkspaceId(Long workspaceId);
}
