package de.tum.cit.aet.hephaestus.mentor;

import de.tum.cit.aet.hephaestus.core.exception.EntityNotFoundException;
import de.tum.cit.aet.hephaestus.core.privacy.spi.PersonDataWriteFence;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Finds and creates Heph conversations. The developer is an account (ADR 0056): one main conversation per account
 * and workspace, found and created by the account alone.
 *
 * <p>Erasure. A write that creates a conversation or stores content about the developer holds the account's
 * person-data write fence first ({@link #holdWrite}), before any ledger lock. An erasure of the account waits for the
 * write, and a write after the erasure was requested is refused, so no conversation or entry outlives the erasure.
 */
@Service
@RequiredArgsConstructor
public class MentorConversationService {

    private final MentorConversationRepository conversations;
    private final MentorEntryRepository entries;
    private final PersonDataWriteFence writeFence;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public MentorConversation require(long workspaceId, UUID conversationId) {
        return conversations
                .findByIdAndWorkspaceId(conversationId, workspaceId)
                .orElseThrow(() -> new EntityNotFoundException("MentorConversation", conversationId.toString()));
    }

    /** The account's main conversation in the workspace, created on its first write. */
    @Transactional
    public MentorConversation findOrCreateMain(long workspaceId, long accountId) {
        holdWrite(writeFence, accountId);
        // Serializes the first write of an account: two first writes would both find no main.
        conversations.lockMainCreation("mentor-main:" + workspaceId + ":" + accountId);
        return conversations
                .findMain(workspaceId, accountId)
                .orElseGet(() -> conversations.save(MentorConversation.main(workspaceId, accountId, clock.instant())));
    }

    /** The account's main conversation, without creating it. */
    @Transactional(readOnly = true)
    public Optional<MentorConversation> findMain(long workspaceId, long accountId) {
        return conversations.findMain(workspaceId, accountId);
    }

    /**
     * The thread on {@code parentEntryId}, created on first use. A thread forks a main conversation at one of its
     * messages: a question, an answer or a weekly digest. Threads do not nest.
     */
    @Transactional
    public MentorConversation openThread(long workspaceId, UUID parentEntryId) {
        UUID mainId = entries.findConversationIdByIdAndWorkspaceId(parentEntryId, workspaceId)
                .orElseThrow(() -> new EntityNotFoundException("MentorEntry", parentEntryId.toString()));
        holdWrite(
                writeFence,
                conversations
                        .findAccountIdByIdAndWorkspaceId(mainId, workspaceId)
                        .orElseThrow(() -> new EntityNotFoundException("MentorConversation", mainId.toString())));
        // The main first, before anything is read: one thread per parent, concurrent openers wait here and the second
        // finds the first's thread; a redaction commits before the reads below.
        MentorConversation main = conversations
                .lockByIdAndWorkspaceId(mainId, workspaceId)
                .orElseThrow(() -> new EntityNotFoundException("MentorConversation", mainId.toString()));
        MentorEntry parent = entries.findByIdAndWorkspaceId(parentEntryId, workspaceId)
                .orElseThrow(() -> new EntityNotFoundException("MentorEntry", parentEntryId.toString()));
        if (main.isThread() || !parent.getKind().isMessage() || parent.isRedacted()) {
            throw new MentorRejectedException(MentorRejectedException.Reason.NOT_A_THREAD_PARENT);
        }
        return conversations
                .findByWorkspaceIdAndParentEntryId(workspaceId, parentEntryId)
                .orElseGet(() -> conversations.save(MentorConversation.thread(main, parentEntryId, clock.instant())));
    }

    /**
     * Holds the person-data write fence of the account a write stores content about, until the transaction commits.
     * Call it before any ledger lock.
     *
     * @throws MentorRejectedException when the account's data was erased or its erasure was requested
     */
    static void holdWrite(PersonDataWriteFence writeFence, long accountId) {
        if (!writeFence.holdForAccountWrite(accountId)) {
            throw new MentorRejectedException(MentorRejectedException.Reason.PERSON_ERASED);
        }
    }

    /**
     * Records that the developer read their conversation up to {@code at}. The marker only moves forward, and never
     * past now: an earlier time, as a second device sends it, changes nothing. A move tells the developer's other open
     * streams after commit ({@link MentorLedgerEvents.ReadChanged}). A developer without a conversation has nothing to
     * read.
     *
     * @return the marker after the call, or {@code null} without a conversation
     */
    @Transactional
    public @Nullable Instant markRead(long workspaceId, long accountId, Instant at) {
        Instant read = at.isAfter(clock.instant()) ? clock.instant() : at;
        holdWrite(writeFence, accountId);
        Optional<MentorConversation> found = conversations.findMain(workspaceId, accountId);
        if (found.isEmpty()) {
            return null;
        }
        MentorConversation main = found.get();
        if (conversations.markRead(main.getId(), workspaceId, read) == 0) {
            // The marker is already here or later, possibly written by a request that committed after the row above
            // was read: the database has the current one.
            return conversations.findLastReadAt(main.getId(), workspaceId);
        }
        events.publishEvent(new MentorLedgerEvents.ReadChanged(workspaceId, main.getId(), main.getAccountId(), read));
        return read;
    }

    /** The main conversation projected into this Slack DM, without creating or moving it. */
    @Transactional(readOnly = true)
    public Optional<MentorConversation> findMainBySlack(
            long workspaceId, String teamId, String slackUserId, String channelId) {
        return conversations.findMainsBySlackIdentity(workspaceId, teamId, slackUserId, channelId).stream()
                .findFirst();
    }

    /** The thread on {@code parentEntryId}, if one was opened. */
    @Transactional(readOnly = true)
    public Optional<MentorConversation> findThread(long workspaceId, UUID parentEntryId) {
        return conversations.findByWorkspaceIdAndParentEntryId(workspaceId, parentEntryId);
    }

    /** Records the Slack DM that this main conversation's top level is projected into. */
    @Transactional
    public MentorConversation bindSlackIdentity(
            long workspaceId, UUID conversationId, String teamId, String userId, String channelId) {
        MentorConversation conversation = require(workspaceId, conversationId);
        conversation.bindSlack(teamId, userId, channelId);
        return conversation;
    }

    /** Forgets every Slack identity in the workspace when its Slack content is erased. */
    @Transactional
    public int clearSlackIdentities(long workspaceId) {
        return conversations.clearSlackIdentitiesByWorkspaceId(workspaceId);
    }
}
