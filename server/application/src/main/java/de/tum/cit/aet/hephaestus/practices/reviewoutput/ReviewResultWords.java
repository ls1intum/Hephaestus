package de.tum.cit.aet.hephaestus.practices.reviewoutput;

import de.tum.cit.aet.hephaestus.evidence.SourceUsePurpose;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackEvidenceRepository;
import de.tum.cit.aet.hephaestus.practices.model.PracticeResult;
import de.tum.cit.aet.hephaestus.practices.result.PracticeResultRepository;
import de.tum.cit.aet.hephaestus.practices.reviewoutput.dto.ReviewBoundResultDTO;
import de.tum.cit.aet.hephaestus.practices.spi.EvidenceAuthorization;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Operator prose follows the source authority of its result; ledger facts remain readable. */
@Component
@RequiredArgsConstructor
class ReviewResultWords {
    private final PracticeResultRepository results;
    private final EvidenceAuthorization authorization;
    private final FeedbackEvidenceRepository feedbackEvidence;

    List<ReviewBoundResultDTO> boundToFeedback(long workspaceId, UUID feedbackId) {
        var bound = feedbackEvidence.findBoundResults(workspaceId, feedbackId);
        var permitted = permitted(
                workspaceId,
                bound.stream().map(row -> row.getPracticeResultId()).toList());
        return bound.stream()
                .map(row -> ReviewBoundResultDTO.from(row, permitted.contains(row.getPracticeResultId())))
                .toList();
    }

    boolean permits(long workspaceId, PracticeResult result) {
        return authorization.permits(workspaceId, result, SourceUsePurpose.OPERATOR_EVIDENCE_REVIEW);
    }

    Set<UUID> permitted(long workspaceId, Collection<UUID> ids) {
        if (ids.isEmpty()) return Set.of();
        return authorization.permitsAll(
                workspaceId,
                results.findForEvidenceAuthorization(ids, workspaceId),
                SourceUsePurpose.OPERATOR_EVIDENCE_REVIEW);
    }
}
