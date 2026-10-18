package de.tum.cit.aet.hephaestus.practices.feedback;

import de.tum.cit.aet.hephaestus.core.exception.EntityNotFoundException;
import de.tum.cit.aet.hephaestus.core.privacy.spi.PersonDataWriteFence;
import de.tum.cit.aet.hephaestus.integration.core.spi.ConditionalOnCaptureRole;
import de.tum.cit.aet.hephaestus.practices.feedback.decision.FeedbackDecisionService;
import de.tum.cit.aet.hephaestus.practices.feedback.delivery.FeedbackCompositionContextService;
import de.tum.cit.aet.hephaestus.practices.spi.ConversationCopyOutcome;
import de.tum.cit.aet.hephaestus.practices.spi.ConversationCopyReceipt;
import de.tum.cit.aet.hephaestus.practices.spi.CopyWriteFence;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackCheck;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackCheckStage;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackCompositionContext;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackCopyKind;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackCopyState;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackDecisionInput;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackDestination;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackDraft;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackResolution;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackSendAction;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackSendState;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackState;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackStopReason;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackStore;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackUsefulness;
import de.tum.cit.aet.hephaestus.practices.spi.PreparedPackage;
import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
@RequiredArgsConstructor
@ConditionalOnCaptureRole
public class FeedbackStoreService implements FeedbackStore {
    private final FeedbackRepository feedback;
    private final FeedbackCopyRepository copies;
    private final FeedbackSendRepository sends;
    private final FeedbackDecisionService decisions;
    private final FeedbackResponseService responses;
    private final FeedbackVisibilityService visibility;
    private final Clock clock;
    private final PersonDataWriteFence privacy;
    private final FeedbackCompositionContextService composition;
    private final FeedbackSendQueue sendQueue;
    private final FeedbackDeliveryPolicy policy;
    private final FeedbackPreparationStore prepared;

    @Override
    @Transactional(readOnly = true)
    public FeedbackCompositionContext compositionContext(
            long workspaceId, UUID reviewId, List<UUID> admittedResultIds) {
        return composition.read(workspaceId, reviewId, admittedResultIds);
    }

    @Override
    public PreparedPackage prepareAll(long workspaceId, UUID reviewId, List<FeedbackDraft> drafts) {
        return prepared.prepareAll(workspaceId, reviewId, drafts);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<CopyWriteFence> claimConversationCopy(long workspaceId, UUID feedbackId, UUID chatMessageId) {
        var existing = feedback.findByIdAndWorkspaceId(feedbackId, workspaceId);
        if (existing.isEmpty() || !privacy.holdForAccountWrite(existing.get().getDeveloperId()))
            return Optional.empty();
        var row = lock(workspaceId, feedbackId);
        if (row.getDestination() != FeedbackDestination.CONVERSATION
                || row.getState() != FeedbackState.READY
                || visibility.withdrawn(workspaceId, feedbackId)
                || !copies.ownsConversationMessage(workspaceId, row.getDeveloperId(), chatMessageId)
                || !check(row, true).allowed()) return Optional.empty();
        var children = copies.lockByFeedbackId(workspaceId, feedbackId);
        if (children.size() != 1) return Optional.empty();
        var copy = children.getFirst();
        if (copy.getKind() != FeedbackCopyKind.CHAT_TURN
                || copy.getState() != FeedbackCopyState.PLANNED
                || copies.claimConversation(workspaceId, feedbackId, copy.getId(), chatMessageId, clock.instant()) != 1)
            return Optional.empty();
        var recorded = copies.findByIdAndWorkspaceId(copy.getId(), workspaceId).orElseThrow();
        feedback.updateDelivery(
                workspaceId,
                feedbackId,
                FeedbackState.UNCONFIRMED,
                FeedbackStopReason.DELIVERY_UNCONFIRMED,
                row.getDeliveredAt());
        return Optional.of(new CopyWriteFence(
                feedbackId, copy.getId(), chatMessageId, Objects.requireNonNull(recorded.getWriteStartedAt())));
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordConversationReceipt(long workspaceId, CopyWriteFence fence, ConversationCopyReceipt receipt) {
        if (!fence.chatMessageId().equals(receipt.chatMessageId())
                || receipt.at().isBefore(fence.writeStartedAt()))
            throw new IllegalArgumentException("The receipt does not belong to this conversation write.");
        var existing =
                feedback.findByIdAndWorkspaceId(fence.feedbackId(), workspaceId).orElseThrow();
        if (!privacy.holdForAccountWrite(existing.getDeveloperId()))
            throw conflict("Recipient processing is suppressed.");
        var row = lock(workspaceId, fence.feedbackId());
        var copy = copies.lockByFeedbackId(workspaceId, fence.feedbackId()).stream()
                .filter(child -> child.getId().equals(fence.copyId()))
                .findFirst()
                .orElseThrow();
        if (row.getDestination() != FeedbackDestination.CONVERSATION
                || copy.getKind() != FeedbackCopyKind.CHAT_TURN
                || !Objects.equals(copy.getChatMessageId(), fence.chatMessageId())
                || !Objects.equals(copy.getWriteStartedAt(), fence.writeStartedAt())
                || !copies.ownsConversationMessage(workspaceId, row.getDeveloperId(), fence.chatMessageId()))
            throw conflict("The conversation write no longer has its recorded authority.");
        if (copy.getPostedAt() != null) return;
        switch (receipt.outcome()) {
            case DELIVERED -> {
                var words = copies.completedConversationText(workspaceId, row.getDeveloperId(), fence.chatMessageId())
                        .filter(text -> !text.isBlank())
                        .orElseThrow(() -> conflict("The completed assistant words are" + " unavailable."));
                if (copies.recordConversationDelivered(
                                workspaceId,
                                fence.feedbackId(),
                                fence.copyId(),
                                fence.chatMessageId(),
                                words,
                                receipt.at())
                        != 1) throw conflict("The conversation receipt could not be recorded.");
                feedback.updateDelivery(
                        workspaceId,
                        fence.feedbackId(),
                        FeedbackState.DELIVERED,
                        null,
                        row.getDeliveredAt() == null ? receipt.at() : row.getDeliveredAt());
            }
            case UNCONFIRMED, FAILED -> {
                boolean unknown = receipt.outcome() == ConversationCopyOutcome.UNCONFIRMED;
                if (copies.recordConversationOutcome(
                                workspaceId,
                                fence.feedbackId(),
                                fence.copyId(),
                                fence.chatMessageId(),
                                unknown ? "WRITE_STARTED" : "FAILED",
                                unknown ? "DELIVERY_UNCONFIRMED" : null,
                                unknown ? FeedbackCopyReadback.UNKNOWN.name() : FeedbackCopyReadback.ABSENT.name(),
                                receipt.reason() == null ? "The conversation was not delivered." : receipt.reason())
                        != 1) throw conflict("The conversation receipt could not be recorded.");
                feedback.updateDelivery(
                        workspaceId,
                        fence.feedbackId(),
                        unknown ? FeedbackState.UNCONFIRMED : FeedbackState.FAILED,
                        unknown ? FeedbackStopReason.DELIVERY_UNCONFIRMED : null,
                        row.getDeliveredAt());
            }
        }
    }

    @Override
    public void decide(long workspaceId, UUID feedbackId, long actorId, FeedbackDecisionInput input) {
        decisions.decide(workspaceId, feedbackId, actorId, input);
    }

    @Override
    public void respond(
            long workspaceId,
            UUID feedbackId,
            long developerId,
            @Nullable FeedbackResolution resolution,
            @Nullable FeedbackUsefulness usefulness,
            @Nullable String comment) {
        responses.respond(workspaceId, feedbackId, developerId, resolution, usefulness, comment);
    }

    @Override
    public void retry(long workspaceId, UUID feedbackId, long actorId) {
        if (actorId <= 0) throw new IllegalArgumentException("A retry requires an authenticated account.");
        var row = lock(workspaceId, feedbackId);
        var send = sends.lockByFeedbackId(workspaceId, feedbackId);
        var children = copies.lockByFeedbackId(workspaceId, feedbackId);
        if (row.getDestination() == FeedbackDestination.ON_WORK && visibility.withdrawn(workspaceId, feedbackId)) {
            retryWithdrawal(row, send, children);
            return;
        }
        if (row.getDestination() != FeedbackDestination.ON_WORK
                || (row.getState() != FeedbackState.FAILED && row.getState() != FeedbackState.DELIVERED)
                || send.filter(s -> s.getState() != FeedbackSendState.DONE).isPresent()
                || children.stream()
                        .anyMatch(c -> c.getState() == FeedbackCopyState.WRITE_STARTED
                                || (c.getState() == FeedbackCopyState.FAILED
                                        && c.getWriteStartedAt() != null
                                        && c.getReadback() != FeedbackCopyReadback.ABSENT)))
            throw conflict("This feedback cannot be retried safely.");
        var check = check(row, true);
        if (!check.allowed()) throw conflict("Current delivery policy does not permit this retry.");
        if (copies.retryAbsent(workspaceId, feedbackId) == 0) throw conflict("No absent copy can be retried.");
        feedback.updateDelivery(
                workspaceId,
                feedbackId,
                row.getDeliveredAt() == null ? FeedbackState.READY : FeedbackState.DELIVERED,
                null,
                row.getDeliveredAt());
        sendQueue.queue(row, FeedbackSendAction.POST, FeedbackSendState.PENDING);
    }

    /**
     * Runs the recorded withdrawal's correction again after it stopped with a posted copy whose removal the provider
     * did not acknowledge. It records no decision and keeps the copies' receipts and the send's attempt history.
     */
    private void retryWithdrawal(Feedback row, Optional<FeedbackSend> send, List<FeedbackCopy> children) {
        if (send.filter(s -> s.getState() != FeedbackSendState.DONE).isPresent())
            throw conflict("The withdrawal is still in progress.");
        if (send.filter(s -> s.getAction() == FeedbackSendAction.RETRACT).isEmpty()
                || children.stream()
                        .noneMatch(c -> c.getState() == FeedbackCopyState.POSTED
                                && c.getExternalRef() != null
                                && c.getReadback() == FeedbackCopyReadback.TRANSIENT))
            throw conflict("No withdrawn copy can be retried.");
        sendQueue.queue(row, FeedbackSendAction.RETRACT, FeedbackSendState.PENDING);
    }

    public FeedbackCheck check(Feedback row, boolean approval) {
        return policy.check(row, FeedbackCheckStage.EGRESS, approval);
    }

    public Feedback lock(long workspaceId, UUID id) {
        return feedback.lockByIdAndWorkspaceId(id, workspaceId)
                .orElseThrow(() -> new EntityNotFoundException("Feedback", id.toString()));
    }

    public void withhold(Feedback row, FeedbackStopReason reason) {
        policy.withhold(row, reason);
    }

    private static ResponseStatusException conflict(String detail) {
        return new ResponseStatusException(HttpStatus.CONFLICT, detail);
    }
}
