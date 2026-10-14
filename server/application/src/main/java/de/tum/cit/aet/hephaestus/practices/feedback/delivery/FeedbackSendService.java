package de.tum.cit.aet.hephaestus.practices.feedback.delivery;

import de.tum.cit.aet.hephaestus.core.runtime.ConditionalOnServerRole;
import de.tum.cit.aet.hephaestus.integration.core.spi.CopyDisposition;
import de.tum.cit.aet.hephaestus.integration.core.spi.CopyReceipt;
import de.tum.cit.aet.hephaestus.integration.core.spi.FeedbackAnchor;
import de.tum.cit.aet.hephaestus.integration.core.spi.FeedbackTarget;
import de.tum.cit.aet.hephaestus.integration.core.spi.LineNote;
import de.tum.cit.aet.hephaestus.integration.core.spi.LineNoteWriter;
import de.tum.cit.aet.hephaestus.integration.core.spi.LookupPresence;
import de.tum.cit.aet.hephaestus.integration.core.spi.SummaryContent;
import de.tum.cit.aet.hephaestus.integration.core.spi.SummaryWriter;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackCopy;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackCopyKind;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackCopyState;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackSendAction;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackStopReason;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnServerRole
@RequiredArgsConstructor
public class FeedbackSendService {
    private static final Logger log = LoggerFactory.getLogger(FeedbackSendService.class);
    private static final int MAX_READBACK_ATTEMPTS = 8;
    private final FeedbackSendTransactions transactions;
    private final FeedbackTargetResolver targets;
    private final List<SummaryWriter> summaries;
    private final List<LineNoteWriter> notes;
    private final PostedCopyCorrector corrector;

    public void send(long workspaceId, UUID feedbackId) {
        String owner = UUID.randomUUID().toString();
        var attempt = transactions.claim(workspaceId, feedbackId, owner);
        if (attempt == null) return;
        try {
            var target = attempt.action() == FeedbackSendAction.RETRACT
                            || attempt.copies().stream().anyMatch(copy -> copy.getWriteStartedAt() != null)
                    ? targets.resolveExisting(attempt.feedback())
                    : targets.resolve(attempt.feedback());
            if (attempt.action() == FeedbackSendAction.RETRACT) {
                corrector.correct(attempt, target, owner);
                return;
            }
            boolean readbackOnly =
                    attempt.copies().stream().anyMatch(copy -> copy.getState() == FeedbackCopyState.WRITE_STARTED);
            if (readbackOnly)
                transactions.withholdCopies(
                        workspaceId,
                        feedbackId,
                        owner,
                        attempt.copies().stream()
                                .filter(copy -> copy.getState() == FeedbackCopyState.PLANNED)
                                .map(FeedbackCopy::getId)
                                .toList(),
                        FeedbackStopReason.DELIVERY_UNCONFIRMED);
            for (var copy : attempt.copies()) {
                if (copy.getKind() == FeedbackCopyKind.SUMMARY
                        && (!readbackOnly || copy.getState() == FeedbackCopyState.WRITE_STARTED))
                    summary(attempt, target, owner, copy);
            }
            anchored(attempt, target, owner, readbackOnly);
        } catch (FeedbackTargetResolver.TargetUnavailable refusal) {
            transactions.refuseTarget(workspaceId, feedbackId, owner, refusal.reason());
        } catch (FeedbackSendTransactions.RecipientWriteRefused refusal) {
            transactions.refuseRecipient(workspaceId, feedbackId, owner);
        } catch (RuntimeException failure) {
            transactions.error(workspaceId, feedbackId, owner);
            log.warn("Feedback send did not settle: workspaceId={}, feedbackId={}", workspaceId, feedbackId);
        } finally {
            transactions.finish(workspaceId, feedbackId, owner, attempt.attempts() >= MAX_READBACK_ATTEMPTS);
        }
    }

    private void summary(
            FeedbackSendTransactions.Attempt attempt, FeedbackTarget target, String owner, FeedbackCopy copy) {
        if (copy.getState() != FeedbackCopyState.PLANNED && copy.getState() != FeedbackCopyState.WRITE_STARTED) return;
        var matching = summaries.stream()
                .filter(writer -> writer.kind() == target.integration().kind())
                .toList();
        if (matching.size() != 1) {
            transactions.withholdCopies(
                    copy.getWorkspaceId(),
                    copy.getFeedbackId(),
                    owner,
                    List.of(copy.getId()),
                    FeedbackStopReason.RECIPIENT_UNAVAILABLE);
            return;
        }
        var writer = matching.getFirst();
        var content = new SummaryContent(
                copy.getId(),
                Objects.requireNonNull(attempt.feedback().getBody()),
                Objects.requireNonNull(copy.getWriteKey()),
                Objects.requireNonNull(copy.getWriteMarker()));
        if (copy.getState() == FeedbackCopyState.WRITE_STARTED) {
            var lookup = writer.findExistingSummary(target, content);
            if (lookup.presence() == LookupPresence.FOUND) {
                var handle = Objects.requireNonNull(lookup.handle());
                transactions.receipt(
                        copy.getWorkspaceId(),
                        copy.getFeedbackId(),
                        owner,
                        List.of(new CopyReceipt(
                                copy.getId(),
                                CopyDisposition.PRESERVED_EXISTING,
                                handle.externalRef(),
                                handle.url(),
                                true,
                                null)));
            } else if (lookup.presence() == LookupPresence.ABSENT)
                transactions.absent(copy.getWorkspaceId(), copy.getFeedbackId(), owner, copy.getId());
            return;
        }
        var handle = transactions.recipientWrite(attempt.feedback().getDeveloperId(), () -> {
            if (!transactions.fence(
                    copy.getWorkspaceId(), copy.getFeedbackId(), owner, List.of(copy.getId()), List.of())) return null;
            return writer.postSummary(target, content);
        });
        if (handle == null) return;
        transactions.receipt(
                copy.getWorkspaceId(),
                copy.getFeedbackId(),
                owner,
                List.of(new CopyReceipt(
                        copy.getId(), CopyDisposition.POSTED, handle.externalRef(), handle.url(), true, null)));
    }

    private void anchored(
            FeedbackSendTransactions.Attempt attempt, FeedbackTarget target, String owner, boolean readbackOnly) {
        var eligible = attempt.copies().stream()
                .filter(c -> c.getKind() == FeedbackCopyKind.LINE_NOTE || c.getKind() == FeedbackCopyKind.FILE_NOTE)
                .filter(c ->
                        c.getState() == FeedbackCopyState.PLANNED || c.getState() == FeedbackCopyState.WRITE_STARTED)
                .toList();
        if (eligible.isEmpty()) return;
        var matching = notes.stream()
                .filter(writer -> writer.kind() == target.integration().kind())
                .toList();
        if (matching.size() != 1) {
            transactions.withholdCopies(
                    attempt.feedback().getWorkspaceId(),
                    attempt.feedback().getId(),
                    owner,
                    eligible.stream().map(FeedbackCopy::getId).toList(),
                    FeedbackStopReason.RECIPIENT_UNAVAILABLE);
            return;
        }
        var writer = matching.getFirst();
        var unresolved = eligible.stream()
                .filter(c -> c.getState() == FeedbackCopyState.WRITE_STARTED)
                .toList();
        if (!unresolved.isEmpty()) {
            var found = writer.findPosted(
                    target, unresolved.stream().map(FeedbackSendService::line).toList());
            if (found == null) return;
            transactions.receipt(
                    attempt.feedback().getWorkspaceId(), attempt.feedback().getId(), owner, found);
            var known = found.stream().map(CopyReceipt::copyId).toList();
            unresolved.stream()
                    .filter(c -> !known.contains(c.getId()))
                    .forEach(c -> transactions.absent(c.getWorkspaceId(), c.getFeedbackId(), owner, c.getId()));
        }
        if (readbackOnly) return;
        var planned = eligible.stream()
                .filter(c -> c.getState() == FeedbackCopyState.PLANNED)
                .map(FeedbackSendService::line)
                .toList();
        if (planned.isEmpty()) return;
        var result = transactions.recipientWrite(
                attempt.feedback().getDeveloperId(),
                () -> writer.postImmutablePackage(
                        target,
                        planned,
                        (attempting, completed) -> transactions.fence(
                                attempt.feedback().getWorkspaceId(),
                                attempt.feedback().getId(),
                                owner,
                                attempting,
                                completed)));
        transactions.receipt(
                attempt.feedback().getWorkspaceId(), attempt.feedback().getId(), owner, result.receipts());
        transactions.withholdCopies(
                attempt.feedback().getWorkspaceId(),
                attempt.feedback().getId(),
                owner,
                result.withheldCopyIds(),
                FeedbackStopReason.VOLUME_CAPPED);
    }

    static LineNote line(FeedbackCopy copy) {
        return new LineNote(
                copy.getId(),
                new FeedbackAnchor(
                        Objects.requireNonNull(copy.getPath()), copy.getStartLine(), copy.getEndLine(), copy.getSide()),
                Objects.requireNonNull(copy.getBody()),
                Objects.requireNonNull(copy.getWriteKey()),
                Objects.requireNonNull(copy.getWriteMarker()));
    }
}
