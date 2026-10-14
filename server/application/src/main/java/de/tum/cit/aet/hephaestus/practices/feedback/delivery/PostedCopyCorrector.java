package de.tum.cit.aet.hephaestus.practices.feedback.delivery;

import de.tum.cit.aet.hephaestus.core.runtime.ConditionalOnServerRole;
import de.tum.cit.aet.hephaestus.integration.core.spi.CopyDisposition;
import de.tum.cit.aet.hephaestus.integration.core.spi.CopyReceipt;
import de.tum.cit.aet.hephaestus.integration.core.spi.CopyUpdateKind;
import de.tum.cit.aet.hephaestus.integration.core.spi.CopyUpdateReceipt;
import de.tum.cit.aet.hephaestus.integration.core.spi.FeedbackTarget;
import de.tum.cit.aet.hephaestus.integration.core.spi.LineNoteWriter;
import de.tum.cit.aet.hephaestus.integration.core.spi.SummaryContent;
import de.tum.cit.aet.hephaestus.integration.core.spi.SummaryHandle;
import de.tum.cit.aet.hephaestus.integration.core.spi.SummaryWriter;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackBasisRepository;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackCopy;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackCopyReadback;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackCopyRepository;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackVisibilityService;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackCopyKind;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackCopyState;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnServerRole
@RequiredArgsConstructor
public class PostedCopyCorrector {
    private final FeedbackBasisRepository basis;
    private final FeedbackCopyRepository copies;
    private final FeedbackVisibilityService visibility;
    private final List<SummaryWriter> summaries;
    private final List<LineNoteWriter> notes;
    private final FeedbackSendTransactions transactions;

    public void correct(FeedbackSendTransactions.Attempt attempt, FeedbackTarget target, String owner) {
        var feedback = attempt.feedback();
        boolean remove = basis.hasIncorrectBasis(feedback.getWorkspaceId(), feedback.getId())
                || visibility.withdrawn(feedback.getWorkspaceId(), feedback.getId());
        if (!remove && !transactions.mayRestore(feedback.getWorkspaceId(), feedback.getId(), owner)) return;
        for (var copy : attempt.copies()) {
            boolean currentRemove = basis.hasIncorrectBasis(feedback.getWorkspaceId(), feedback.getId())
                    || visibility.withdrawn(feedback.getWorkspaceId(), feedback.getId());
            if (currentRemove != remove || !transactions.holdsLease(feedback.getWorkspaceId(), feedback.getId(), owner))
                return;
            if (copy.getState() != FeedbackCopyState.POSTED && copy.getState() != FeedbackCopyState.RETRACTED) continue;
            if (copy.getExternalRef() == null
                    || (copy.getKind() == FeedbackCopyKind.SUMMARY && copy.getWriteKey() == null)) {
                transactions.correctionError(
                        copy.getWorkspaceId(),
                        copy.getFeedbackId(),
                        owner,
                        copy.getId(),
                        null,
                        "The historical receipt cannot identify a safe provider update.");
                continue;
            }
            if (copy.getKind() == FeedbackCopyKind.SUMMARY) {
                if (copies.hasNewerSummaryReceipt(
                        copy.getWorkspaceId(), copy.getFeedbackId(), Objects.requireNonNull(copy.getExternalRef()))) {
                    transactions.correctionError(
                            copy.getWorkspaceId(),
                            copy.getFeedbackId(),
                            owner,
                            copy.getId(),
                            null,
                            "A newer piece of feedback owns this provider reference.");
                    continue;
                }
                var matching = summaries.stream()
                        .filter(w -> w.kind() == target.integration().kind())
                        .toList();
                if (matching.size() != 1) {
                    unsupported(copy, owner);
                    continue;
                }
                String body = remove ? "This feedback was withdrawn." : Objects.requireNonNull(feedback.getBody());
                var result = transactions.recipientWrite(
                        feedback.getDeveloperId(),
                        () -> matching.getFirst()
                                .updateSummary(
                                        target,
                                        new SummaryHandle(
                                                Objects.requireNonNull(copy.getExternalRef()), copy.getExternalUrl()),
                                        new SummaryContent(
                                                copy.getId(),
                                                body,
                                                Objects.requireNonNull(copy.getWriteKey()),
                                                copy.getWriteMarker())));
                apply(copy, result, remove, owner);
            } else if (remove && copy.getState() == FeedbackCopyState.POSTED) {
                var matching = notes.stream()
                        .filter(w -> w.kind() == target.integration().kind())
                        .toList();
                if (matching.size() != 1) {
                    unsupported(copy, owner);
                    continue;
                }
                var updates = transactions.recipientWrite(
                        feedback.getDeveloperId(),
                        () -> matching.getFirst()
                                .retract(
                                        target,
                                        List.of(new CopyReceipt(
                                                copy.getId(),
                                                CopyDisposition.POSTED,
                                                copy.getExternalRef(),
                                                copy.getExternalUrl(),
                                                true,
                                                null))));
                var update = updates.stream()
                        .filter(r -> r.copyId().equals(copy.getId()))
                        .findFirst();
                if (update.isPresent()) apply(copy, update.get(), true, owner);
                else
                    transactions.correctionError(
                            copy.getWorkspaceId(),
                            copy.getFeedbackId(),
                            owner,
                            copy.getId(),
                            FeedbackCopyReadback.TRANSIENT,
                            " Correction receipt is unavailable.");
            } else if (!remove && copy.getState() == FeedbackCopyState.RETRACTED) {
                transactions.correctionError(
                        copy.getWorkspaceId(),
                        copy.getFeedbackId(),
                        owner,
                        copy.getId(),
                        null,
                        "Restoring a deleted anchored note is unsupported. The original receipt"
                                + " remains retracted.");
            }
        }
    }

    private void apply(FeedbackCopy copy, CopyUpdateReceipt receipt, boolean remove, String owner) {
        if (!copy.getId().equals(receipt.copyId())
                || (receipt.externalRef() != null && !Objects.equals(copy.getExternalRef(), receipt.externalRef())))
            throw new IllegalArgumentException("Correction must address the original copy.");
        if (receipt.kind() == CopyUpdateKind.EDITED || receipt.kind() == CopyUpdateKind.GONE) {
            transactions.correction(
                    copy.getWorkspaceId(),
                    copy.getFeedbackId(),
                    owner,
                    copy.getId(),
                    remove || receipt.kind() == CopyUpdateKind.GONE
                            ? FeedbackCopyState.RETRACTED.name()
                            : FeedbackCopyState.POSTED.name());
        } else if (receipt.kind() == CopyUpdateKind.UNSUPPORTED) unsupported(copy, owner);
        else
            transactions.correctionError(
                    copy.getWorkspaceId(),
                    copy.getFeedbackId(),
                    owner,
                    copy.getId(),
                    FeedbackCopyReadback.TRANSIENT,
                    " Provider correction is not confirmed.");
    }

    private void unsupported(FeedbackCopy copy, String owner) {
        transactions.correctionError(
                copy.getWorkspaceId(),
                copy.getFeedbackId(),
                owner,
                copy.getId(),
                null,
                "This provider cannot correct the posted copy.");
    }
}
