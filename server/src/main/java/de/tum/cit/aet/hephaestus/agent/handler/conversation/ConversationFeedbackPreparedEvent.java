package de.tum.cit.aet.hephaestus.agent.handler.conversation;

import org.springframework.modulith.NamedInterface;

/**
 * Published by {@link ConversationalFeedbackPreparer} once per recipient that received newly PREPARED
 * CONVERSATION units this cycle, from inside the preparer's REQUIRES_NEW transaction — so an
 * {@code AFTER_COMMIT} listener only ever sees units that are actually persisted. Carries a count, never
 * finding content: the consumer's job is a push signal ("something is waiting"), not delivery.
 *
 * <p>Type-level {@code conversation-nudge} named interface: exposes exactly this record to the Slack
 * adapter without opening the rest of this package (preparer/sweeper/router stay internal).
 *
 * @param workspaceId     the workspace scope
 * @param recipientUserId the SCM {@code User} id the units are prepared for
 * @param unitCount       units newly prepared for this recipient this cycle (&gt; 0)
 */
@NamedInterface("conversation-nudge")
public record ConversationFeedbackPreparedEvent(Long workspaceId, Long recipientUserId, int unitCount) {}
