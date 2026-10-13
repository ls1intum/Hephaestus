package de.tum.cit.aet.hephaestus.practices.reviewoutput;

import de.tum.cit.aet.hephaestus.evidence.SourceUsePurpose;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackObservationRepository;
import de.tum.cit.aet.hephaestus.practices.model.Observation;
import de.tum.cit.aet.hephaestus.practices.observation.ObservationRepository;
import de.tum.cit.aet.hephaestus.practices.reviewoutput.dto.ReviewBoundObservationDTO;
import de.tum.cit.aet.hephaestus.practices.spi.EvidenceAuthorization;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Operator prose follows the source authority of its observation; ledger facts remain readable. */
@Component
@RequiredArgsConstructor
class ReviewObservationWords {
    private final ObservationRepository observations;
    private final EvidenceAuthorization authorization;
    private final FeedbackObservationRepository feedbackObservations;

    List<ReviewBoundObservationDTO> boundToFeedback(long workspaceId, UUID feedbackId) {
        var bound = feedbackObservations.findBoundObservations(workspaceId, feedbackId);
        var permitted = permitted(
                workspaceId, bound.stream().map(row -> row.getObservationId()).toList());
        return bound.stream()
                .map(row -> ReviewBoundObservationDTO.from(row, permitted.contains(row.getObservationId())))
                .toList();
    }

    boolean permits(long workspaceId, Observation observation) {
        return authorization.permits(workspaceId, observation, SourceUsePurpose.OPERATOR_EVIDENCE_REVIEW);
    }

    Set<UUID> permitted(long workspaceId, Collection<UUID> ids) {
        if (ids.isEmpty()) return Set.of();
        return authorization.permitsAll(
                workspaceId,
                observations.findAllByIdInAndWorkspaceId(ids, workspaceId),
                SourceUsePurpose.OPERATOR_EVIDENCE_REVIEW);
    }
}
