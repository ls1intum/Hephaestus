package de.tum.cit.aet.hephaestus.integration.slack.refs;

import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;

/**
 * Value-only Slack channel reference.
 *
 * <p>NEVER an {@code @Entity}. Salesforce 2025-05 Slack API ToS prohibits
 * persistence of Slack message content. While channel metadata (name, archived
 * status) is permitted, we keep all Slack-shaped refs as value records to make
 * the no-persistence boundary trivial to enforce by inspection and via ArchUnit.
 * Channel metadata that we DO persist (Connection.config, scope cache) lives on
 * the existing {@code connection} table — not here.
 *
 * <p>Companion to {@link de.tum.cit.aet.hephaestus.integration.messaging.refs.SlackMessageRef}.
 *
 * @param workspaceId Hephaestus workspace id this ref is scoped to
 * @param teamId      Slack {@code team_id}
 * @param channelId   Slack channel id (e.g. {@code C0123456789})
 * @param channelName optional convenience for log/UI strings; never trusted for
 *                    auth or routing — channel ids are the stable handle
 */
public record SlackChannelRef(
    long workspaceId,
    @NonNull String teamId,
    @NonNull String channelId,
    @Nullable String channelName
) {
}
