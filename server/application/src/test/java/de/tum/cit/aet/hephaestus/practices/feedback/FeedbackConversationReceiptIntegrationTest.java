package de.tum.cit.aet.hephaestus.practices.feedback;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.tum.cit.aet.hephaestus.practices.feedback.decision.FeedbackContentDigest;
import de.tum.cit.aet.hephaestus.practices.spi.ConversationCopyOutcome;
import de.tum.cit.aet.hephaestus.practices.spi.ConversationCopyReceipt;
import de.tum.cit.aet.hephaestus.practices.spi.CopyWriteFence;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackBasisInput;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackCopyInput;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackCopyKind;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackCopyState;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackDestination;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackDraft;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackIds;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackState;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

class FeedbackConversationReceiptIntegrationTest extends AbstractFeedbackDeliveryIntegrationTest {
    @Autowired
    FeedbackContentDigest digests;

    @Autowired
    FeedbackCopyCleanupCustody custody;

    UUID conversationId, copyId, messageId;

    @BeforeEach
    void prepareConversation() {
        conversationId =
                FeedbackIds.conversation(workspace.getId(), reviewId, developerId, List.of(supportingResult.getId()));
        copyId = FeedbackIds.copy(conversationId, FeedbackCopyKind.CHAT_TURN, 0);
        messageId = message(developerId);
        var draft = new FeedbackDraft(
                conversationId,
                workspace.getId(),
                reviewId,
                itemId,
                developerId,
                FeedbackDestination.CONVERSATION,
                FeedbackState.READY,
                null,
                "Discuss focused regression tests.",
                List.of(new FeedbackBasisInput(supportingResult.getId(), null, 0)),
                List.of(new FeedbackCopyInput(
                        copyId, FeedbackCopyKind.CHAT_TURN, 0, null, null, null, null, null, copyId.toString(), null)),
                Map.of());
        store.prepareAll(workspace.getId(), reviewId, List.of(draft));
    }

    private UUID message(long developer) {
        UUID thread = UUID.randomUUID(), id = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO chat_thread(id,workspace_id,account_id,created_at,surface) VALUES"
                        + " (?,?,?,now(),'WEB')",
                thread,
                workspace.getId(),
                developer);
        jdbc.update(
                "INSERT INTO"
                        + " chat_message(id,thread_id,workspace_id,role,status,created_at,parts,version)"
                        + " VALUES (?,?,?,'ASSISTANT','in_flight',now(),'[]'::jsonb,0)",
                id,
                thread,
                workspace.getId());
        return id;
    }

    private CopyWriteFence claim() {
        return store.claimConversationCopy(workspace.getId(), conversationId, messageId)
                .orElseThrow();
    }

    private void completeMessage() {
        jdbc.update(
                "UPDATE chat_message SET status='completed',parts=?::jsonb WHERE id=? AND" + " workspace_id=?",
                "[{\"type\":\"text\",\"text\":\"Some context. \"},{\"type\":\"text\",\"text\":\"Try"
                        + " a focused test.\"}]",
                messageId,
                workspace.getId());
    }

    private FeedbackCopy chatCopy() {
        return copies.findById(copyId).orElseThrow();
    }

    private Feedback conversation() {
        return feedback.findById(conversationId).orElseThrow();
    }

    @Test
    void shouldCommitOneCopyAndMessageFenceBeforeReturningAClaim() {
        var fence = claim();
        assertThat(fence.feedbackId()).isEqualTo(conversationId);
        assertThat(fence.copyId()).isEqualTo(copyId);
        assertThat(fence.chatMessageId()).isEqualTo(messageId);
        assertThat(chatCopy().getWriteStartedAt()).isEqualTo(fence.writeStartedAt());
        assertThat(chatCopy().getChatMessageId()).isEqualTo(messageId);
        assertThat(chatCopy().getState()).isEqualTo(FeedbackCopyState.WRITE_STARTED);
        assertThat(chatCopy().getBody()).isNull();
        assertThat(conversation().getState()).isEqualTo(FeedbackState.UNCONFIRMED);
        assertThat(conversation().getDeliveredAt()).isNull();
        assertThat(store.claimConversationCopy(workspace.getId(), conversationId, messageId))
                .isEmpty();
        assertThat(store.claimConversationCopy(workspace.getId() + 1, conversationId, messageId))
                .isEmpty();
    }

    @Test
    void shouldKeepTheFenceCommittedWhenAnEnclosingTransactionRollsBack() {
        assertThatThrownBy(() -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                    claim();
                    throw new IllegalStateException("outer rollback");
                }))
                .isInstanceOf(IllegalStateException.class);
        assertThat(chatCopy().getWriteStartedAt()).isNotNull();
        assertThat(conversation().getState()).isEqualTo(FeedbackState.UNCONFIRMED);
        assertThat(store.claimConversationCopy(workspace.getId(), conversationId, messageId))
                .isEmpty();
    }

    @Test
    void shouldRecordOnlyTheActualCompletedAssistantWordsAndPreserveApprovalContent() {
        var approvedContent = digests.current(workspace.getId(), conversationId);
        var fence = claim();
        completeMessage();
        Instant at = fence.writeStartedAt().plusSeconds(1);
        store.recordConversationReceipt(
                workspace.getId(),
                fence,
                new ConversationCopyReceipt(messageId, ConversationCopyOutcome.DELIVERED, at, null));
        assertThat(chatCopy().getBody()).isEqualTo("Some context. Try a focused test.");
        assertThat(chatCopy().getBody()).isNotEqualTo(conversation().getBody());
        assertThat(chatCopy().getPostedAt()).isEqualTo(at);
        assertThat(chatCopy().getState()).isEqualTo(FeedbackCopyState.POSTED);
        assertThat(conversation().getState()).isEqualTo(FeedbackState.DELIVERED);
        assertThat(conversation().getDeliveredAt()).isEqualTo(at);
        assertThat(digests.current(workspace.getId(), conversationId)).isEqualTo(approvedContent);
        store.recordConversationReceipt(
                workspace.getId(),
                fence,
                new ConversationCopyReceipt(
                        messageId, ConversationCopyOutcome.FAILED, at.plusSeconds(1), "Late failure"));
        assertThat(chatCopy().getPostedAt()).isEqualTo(at);
        assertThat(conversation().getState()).isEqualTo(FeedbackState.DELIVERED);
    }

    @Test
    void shouldPreserveUncertaintyAndFailureWithoutReopeningTheConversationCopy() {
        var fence = claim();
        store.recordConversationReceipt(
                workspace.getId(),
                fence,
                new ConversationCopyReceipt(
                        messageId,
                        ConversationCopyOutcome.UNCONFIRMED,
                        fence.writeStartedAt().plusSeconds(1),
                        "No transport confirmation"));
        assertThat(conversation().getState()).isEqualTo(FeedbackState.UNCONFIRMED);
        assertThat(chatCopy().getState()).isEqualTo(FeedbackCopyState.WRITE_STARTED);
        assertThatThrownBy(() -> custody.requireAcknowledged(workspace.getId(), List.of(conversationId)))
                .isInstanceOf(ResponseStatusException.class);
        store.recordConversationReceipt(
                workspace.getId(),
                fence,
                new ConversationCopyReceipt(
                        messageId,
                        ConversationCopyOutcome.FAILED,
                        fence.writeStartedAt().plusSeconds(2),
                        "Transport failed"));
        assertThat(conversation().getState()).isEqualTo(FeedbackState.FAILED);
        assertThat(chatCopy().getState()).isEqualTo(FeedbackCopyState.FAILED);
        assertThat(chatCopy().getWriteStartedAt()).isEqualTo(fence.writeStartedAt());
        assertThat(chatCopy().getChatMessageId()).isEqualTo(messageId);
        assertThat(chatCopy().getBody()).isNull();
        assertThat(conversation().getDeliveredAt()).isNull();
        assertThat(store.claimConversationCopy(workspace.getId(), conversationId, messageId))
                .isEmpty();
        assertThat(sends.findById(conversationId)).isEmpty();
        assertThatCode(() -> custody.requireAcknowledged(workspace.getId(), List.of(conversationId)))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldRefuseAPeerMessageAndKeepTheCopyUnclaimed() {
        long other =
                Objects.requireNonNull(persistAccount("Another message owner").getId());
        var foreign = message(other);
        assertThat(store.claimConversationCopy(workspace.getId(), conversationId, foreign))
                .isEmpty();
        assertThat(chatCopy().getState()).isEqualTo(FeedbackCopyState.PLANNED);
        assertThat(chatCopy().getWriteStartedAt()).isNull();
        assertThat(conversation().getState()).isEqualTo(FeedbackState.READY);
    }

    @Test
    void shouldRefuseForgedCopyMessageTimeOrWorkspaceBindings() {
        var fence = claim();
        var receipt = new ConversationCopyReceipt(
                messageId,
                ConversationCopyOutcome.UNCONFIRMED,
                fence.writeStartedAt().plusSeconds(2),
                null);
        assertThatThrownBy(() -> store.recordConversationReceipt(
                        workspace.getId(),
                        new CopyWriteFence(conversationId, UUID.randomUUID(), messageId, fence.writeStartedAt()),
                        receipt))
                .isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> store.recordConversationReceipt(
                        workspace.getId(),
                        new CopyWriteFence(
                                conversationId,
                                copyId,
                                messageId,
                                fence.writeStartedAt().plusSeconds(1)),
                        receipt))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> store.recordConversationReceipt(
                        workspace.getId(),
                        fence,
                        new ConversationCopyReceipt(
                                UUID.randomUUID(), ConversationCopyOutcome.UNCONFIRMED, receipt.at(), null)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> store.recordConversationReceipt(workspace.getId() + 1, fence, receipt))
                .isInstanceOf(RuntimeException.class);
        assertThat(chatCopy().getWriteStartedAt()).isEqualTo(fence.writeStartedAt());
        assertThat(chatCopy().getPostedAt()).isNull();
    }

    @Test
    void shouldRefuseADeliveryClaimWithoutCompletedAssistantText() {
        var fence = claim();
        assertThatThrownBy(() -> store.recordConversationReceipt(
                        workspace.getId(),
                        fence,
                        new ConversationCopyReceipt(
                                messageId,
                                ConversationCopyOutcome.DELIVERED,
                                fence.writeStartedAt().plusSeconds(1),
                                null)))
                .isInstanceOf(ResponseStatusException.class);
        assertThat(conversation().getState()).isEqualTo(FeedbackState.UNCONFIRMED);
        assertThat(chatCopy().getPostedAt()).isNull();
        assertThat(chatCopy().getBody()).isNull();
    }

    @Test
    void shouldRefuseAMarkedBasisBeforeCommittingAnyCopyFence() {
        jdbc.update(
                "INSERT INTO result_mark(id,workspace_id,result_id,kind,reason,created_at)"
                        + " VALUES (?,?,?,'INCORRECT','The claim needs correction.',now())",
                UUID.randomUUID(),
                workspace.getId(),
                supportingResult.getId());
        assertThat(store.claimConversationCopy(workspace.getId(), conversationId, messageId))
                .isEmpty();
        assertThat(chatCopy().getWriteStartedAt()).isNull();
        assertThat(chatCopy().getState()).isEqualTo(FeedbackCopyState.PLANNED);
    }
}
