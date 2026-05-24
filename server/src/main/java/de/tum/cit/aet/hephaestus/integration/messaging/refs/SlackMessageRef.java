package de.tum.cit.aet.hephaestus.integration.messaging.refs;

import org.springframework.lang.NonNull;

/**
 * Value-only Slack message reference.
 *
 * <p>NEVER an {@code @Entity}. Salesforce 2025-05 Slack API ToS prohibits persistence of
 * Slack message content. Hephaestus stores only identity (team_id + channel_id + ts);
 * content is fetched on demand and treated as ephemeral. ArchUnit enforces this.
 */
public record SlackMessageRef(
    long workspaceId,
    @NonNull String teamId,
    @NonNull String channelId,
    @NonNull String ts
) {
}
