package de.tum.cit.aet.hephaestus.practices.reviewoutput;

import de.tum.cit.aet.hephaestus.core.exception.EntityNotFoundException;
import de.tum.cit.aet.hephaestus.integration.core.signal.ArtifactKind;
import de.tum.cit.aet.hephaestus.practices.feedback.Feedback;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackObservationRepository;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackPlacementRepository;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackQueryFilter;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackRepository;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackRepository.OperatorFeedbackRow;
import de.tum.cit.aet.hephaestus.practices.reviewoutput.ReviewArtifactResolver.ArtifactRef;
import de.tum.cit.aet.hephaestus.practices.reviewoutput.dto.ReviewBoundFindingDTO;
import de.tum.cit.aet.hephaestus.practices.reviewoutput.dto.ReviewFeedbackDTO;
import de.tum.cit.aet.hephaestus.practices.reviewoutput.dto.ReviewFeedbackDetailDTO;
import de.tum.cit.aet.hephaestus.practices.reviewoutput.dto.ReviewPlacementDTO;
import de.tum.cit.aet.hephaestus.practices.reviewoutput.dto.ReviewSubjectDTO;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
class ReviewFeedbackQueryService {

    private final FeedbackRepository feedbackRepository;
    private final FeedbackObservationRepository feedbackObservationRepository;
    private final FeedbackPlacementRepository feedbackPlacementRepository;
    private final ReviewSubjectResolver subjectResolver;
    private final ReviewArtifactResolver artifactResolver;

    @Transactional(readOnly = true)
    Page<ReviewFeedbackDTO> list(Long workspaceId, FeedbackQueryFilter filter, Pageable pageable) {
        Page<OperatorFeedbackRow> rows = feedbackRepository.findForWorkspace(workspaceId, filter, pageable);
        List<Long> userIds = new ArrayList<>(rows.getNumberOfElements() * 2);
        for (OperatorFeedbackRow row : rows) {
            userIds.add(row.getRecipientUserId());
            userIds.add(row.getAboutUserId());
        }
        Map<Long, ReviewSubjectDTO> subjects = subjectResolver.resolve(userIds);
        var artifacts = artifactResolver.resolve(
            workspaceId,
            rows
                .getContent()
                .stream()
                .filter(row -> row.getArtifactKind() != null && row.getArtifactId() != null)
                .map(row ->
                    new ArtifactRef(row.getAgentJobId(), ArtifactKind.of(row.getArtifactKind()), row.getArtifactId())
                )
                .toList()
        );
        return rows.map(row -> {
            var artifact =
                row.getArtifactKind() == null || row.getArtifactId() == null
                    ? null
                    : artifacts.get(
                          new ArtifactRef(
                              row.getAgentJobId(),
                              ArtifactKind.of(row.getArtifactKind()),
                              row.getArtifactId()
                          )
                      );
            return ReviewFeedbackDTO.from(row, artifact, subjects);
        });
    }

    @Transactional(readOnly = true)
    ReviewFeedbackDetailDTO get(Long workspaceId, UUID feedbackId) {
        Feedback feedback = feedbackRepository
            .findByIdAndWorkspaceId(feedbackId, workspaceId)
            .orElseThrow(() -> new EntityNotFoundException("Feedback", feedbackId.toString()));
        List<ReviewBoundFindingDTO> findings = feedbackObservationRepository
            .findBoundObservations(workspaceId, feedbackId)
            .stream()
            .map(ReviewBoundFindingDTO::from)
            .toList();
        List<ReviewPlacementDTO> placements = feedbackPlacementRepository
            .findByFeedbackIdInDisplayOrder(feedbackId)
            .stream()
            .map(ReviewPlacementDTO::from)
            .toList();
        Map<Long, ReviewSubjectDTO> subjects = subjectResolver.resolve(
            List.of(feedback.getRecipientUserId(), feedback.getAboutUserId())
        );
        var artifactKey =
            feedback.getArtifactKind() == null || feedback.getArtifactId() == null
                ? null
                : new ArtifactRef(feedback.getAgentJobId(), feedback.getArtifactKind(), feedback.getArtifactId());
        var artifact =
            artifactKey == null ? null : artifactResolver.resolve(workspaceId, List.of(artifactKey)).get(artifactKey);
        return ReviewFeedbackDetailDTO.from(
            feedback,
            artifact,
            subjects.get(feedback.getRecipientUserId()),
            subjects.get(feedback.getAboutUserId()),
            findings,
            placements
        );
    }
}
