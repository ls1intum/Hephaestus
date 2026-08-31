package de.tum.cit.aet.hephaestus.practices.reviewoutput;

import de.tum.cit.aet.hephaestus.core.exception.EntityNotFoundException;
import de.tum.cit.aet.hephaestus.evidence.SourceUsePurpose;
import de.tum.cit.aet.hephaestus.integration.core.signal.ArtifactKind;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackObservationRepository;
import de.tum.cit.aet.hephaestus.practices.model.Observation;
import de.tum.cit.aet.hephaestus.practices.observation.ObservationQueryFilter;
import de.tum.cit.aet.hephaestus.practices.observation.ObservationRepository;
import de.tum.cit.aet.hephaestus.practices.observation.ObservationRepository.ObservationFeedbackDisposition;
import de.tum.cit.aet.hephaestus.practices.observation.ObservationRepository.OperatorObservationRow;
import de.tum.cit.aet.hephaestus.practices.reviewoutput.ReviewArtifactResolver.ArtifactRef;
import de.tum.cit.aet.hephaestus.practices.reviewoutput.dto.ReviewArtifactDTO;
import de.tum.cit.aet.hephaestus.practices.reviewoutput.dto.ReviewBoundFeedbackDTO;
import de.tum.cit.aet.hephaestus.practices.reviewoutput.dto.ReviewObservationDTO;
import de.tum.cit.aet.hephaestus.practices.reviewoutput.dto.ReviewObservationDetailDTO;
import de.tum.cit.aet.hephaestus.practices.reviewoutput.dto.ReviewSubjectDTO;
import de.tum.cit.aet.hephaestus.practices.spi.EvidenceAuthorization;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
class ReviewObservationQueryService {

    private final ObservationRepository observationRepository;
    private final FeedbackObservationRepository feedbackObservationRepository;
    private final ReviewSubjectResolver subjectResolver;
    private final ReviewArtifactResolver artifactResolver;
    private final EvidenceAuthorization evidenceAuthorization;

    @Transactional(readOnly = true)
    public Page<ReviewObservationDTO> list(
            Long workspaceId, ObservationQueryFilter filter, ReviewObservationSort sort, Pageable pageable) {
        Page<OperatorObservationRow> rows = observationRepository.findForWorkspace(
                workspaceId, filter, sort == ReviewObservationSort.ACTIONABILITY, pageable);
        Map<Long, ReviewSubjectDTO> subjects = subjectResolver.resolve(rows.getContent().stream()
                .map(OperatorObservationRow::getAboutUserId)
                .toList());
        Map<UUID, ObservationFeedbackDisposition> dispositions = rows.isEmpty()
                ? Map.of()
                : observationRepository
                        .findFeedbackDispositions(
                                workspaceId,
                                rows.getContent().stream()
                                        .map(OperatorObservationRow::getId)
                                        .toList())
                        .stream()
                        .collect(Collectors.toMap(
                                ObservationFeedbackDisposition::getObservationId, Function.identity()));
        Map<ArtifactRef, ReviewArtifactDTO> artifacts = artifactResolver.resolve(
                workspaceId,
                rows.getContent().stream()
                        .map(row -> new ArtifactRef(
                                row.getAgentJobId(), ArtifactKind.of(row.getArtifactKind()), row.getArtifactId()))
                        .toList());
        return rows.map(row -> {
            ArtifactRef key =
                    new ArtifactRef(row.getAgentJobId(), ArtifactKind.of(row.getArtifactKind()), row.getArtifactId());
            return ReviewObservationDTO.from(
                    row, dispositions.get(row.getId()), Objects.requireNonNull(artifacts.get(key)), subjects);
        });
    }

    @Transactional(readOnly = true)
    public ReviewObservationDetailDTO get(Long workspaceId, UUID observationId) {
        Observation observation = observationRepository
                .findByIdAndWorkspaceId(observationId, workspaceId)
                .orElseThrow(() -> new EntityNotFoundException("Observation", observationId.toString()));
        List<ReviewBoundFeedbackDTO> feedback =
                feedbackObservationRepository.findBoundFeedbackUnits(workspaceId, observationId).stream()
                        .map(ReviewBoundFeedbackDTO::from)
                        .toList();
        ReviewSubjectDTO subject =
                subjectResolver.resolve(List.of(observation.getAboutUserId())).get(observation.getAboutUserId());
        ArtifactRef artifactKey = new ArtifactRef(
                observation.getAgentJobId(), observation.getArtifactKind(), observation.getArtifactId());
        var artifact =
                artifactResolver.resolve(workspaceId, List.of(artifactKey)).get(artifactKey);
        boolean includeEvidence =
                evidenceAuthorization.permits(workspaceId, observation, SourceUsePurpose.OPERATOR_EVIDENCE_REVIEW);
        return ReviewObservationDetailDTO.from(
                observation, Objects.requireNonNull(artifact), subject, feedback, includeEvidence);
    }
}
