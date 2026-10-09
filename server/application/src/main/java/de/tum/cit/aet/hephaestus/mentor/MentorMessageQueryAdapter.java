package de.tum.cit.aet.hephaestus.mentor;

import de.tum.cit.aet.hephaestus.mentor.spi.MentorMessageQuery;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * The words of one message the developer wrote to Heph: a {@code USER} entry of a conversation the account owns, not
 * redacted. Any other entry, or one of another account or workspace, has no text here.
 */
@Component
@RequiredArgsConstructor
class MentorMessageQueryAdapter implements MentorMessageQuery {
    private final MentorEntryRepository entries;
    private final MentorConversationRepository conversations;

    @Override
    @Transactional(readOnly = true)
    public Optional<String> textForMessage(long workspaceId, long accountId, UUID messageId) {
        return entries.findByIdAndWorkspaceId(messageId, workspaceId)
                .filter(entry -> entry.getKind() == MentorEntry.Kind.USER && !entry.isRedacted())
                .filter(entry -> conversations
                        .findByIdAndWorkspaceId(entry.getConversationId(), workspaceId)
                        .filter(conversation -> conversation.getAccountId() == accountId)
                        .isPresent())
                .map(MentorEntry::getText);
    }
}
