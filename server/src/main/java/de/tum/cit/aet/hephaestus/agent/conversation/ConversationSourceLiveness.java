package de.tum.cit.aet.hephaestus.agent.conversation;

import java.util.Collection;
import java.util.Set;

/**
 * Agent-owned SPI: the fail-closed liveness check for {@code CONVERSATION_THREAD}-derived rows. Implemented by
 * {@code integration.slack} (which owns the {@code slack_thread}/{@code slack_monitored_channel} join); the agent
 * {@code ConversationConsentGate} delegates to it so the mentor derived-feedback content sources never read the
 * Slack schema.
 *
 * <p>A {@code CONVERSATION_THREAD} observation/feedback/prepared-fact carries model output composed over the raw
 * messages of a Slack thread, so it may only reach the mentor while that thread's source channel consent is still
 * {@code ACTIVE} — the exact gate the raw projection applies on the message read. This SPI resolves which of a
 * candidate thread-id set is still live.
 */
public interface ConversationSourceLiveness {
    /**
     * The subset of {@code threadIds} whose source Slack channel is still {@code consent_state = 'ACTIVE'} in this
     * workspace — the consent-gated allow-set. A paused/revoked/erased channel, or a deleted thread, contributes
     * no id, so its derived row is withheld (fail-closed). An empty input returns an empty set without querying.
     */
    Set<Long> activeThreadIds(long workspaceId, Collection<Long> threadIds);
}
