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
}
