package de.tum.cit.aet.hephaestus.practices.spi;

import java.util.Collection;

/** Deletes derived rows scoped by workspace, item.kind = chat.thread, and item.source_id.
 * Thread erasure selects the supplied source ids; person erasure selects result.developer_id.
 * Feedback is selected through feedback_basis before the corresponding results are deleted.
 */
public interface ConversationFeedbackErasure {
    int eraseForThreads(long workspaceId, Collection<Long> slackThreadIds);

    int eraseAllConversationForWorkspace(long workspaceId);

    int eraseConversationFeedbackAboutUser(long workspaceId, long aboutUserId);
}
