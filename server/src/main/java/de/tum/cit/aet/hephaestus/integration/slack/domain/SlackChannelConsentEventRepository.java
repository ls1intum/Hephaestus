package de.tum.cit.aet.hephaestus.integration.slack.domain;

import java.util.List;
import org.springframework.data.repository.Repository;

/**
 * Insert-and-read-only access to the Slack channel consent-transition audit trail. The interface deliberately
 * extends the narrow {@link Repository} base (not {@code JpaRepository}) and exposes ONLY {@link #save} (append a
 * transition) and a workspace-scoped chronological read — there is no update or delete method, so the audit trail
 * is immutable through this surface, matching the {@code @Immutable} entity.
 *
 * <p>Every read carries the {@code workspace_id} predicate the tenancy {@code StatementInspector} requires;
 * inserts are exempt (a row that already names its workspace cannot leak across tenants).
 */
public interface SlackChannelConsentEventRepository extends Repository<SlackChannelConsentEvent, Long> {
    /** Append one consent transition. Insert-only: the entity is {@code @Immutable}, so this never updates. */
    SlackChannelConsentEvent save(SlackChannelConsentEvent event);

    /** The full transition history of one channel, oldest first — the admin audit listing. */
    List<SlackChannelConsentEvent> findByWorkspaceIdAndSlackChannelIdOrderByCreatedAtAscIdAsc(
        Long workspaceId,
        String slackChannelId
    );
}
