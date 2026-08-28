package de.tum.cit.aet.hephaestus.practices.reviewoutput;

import de.tum.cit.aet.hephaestus.core.exception.EntityNotFoundException;
import de.tum.cit.aet.hephaestus.integration.core.signal.ArtifactKind;
import de.tum.cit.aet.hephaestus.practices.feedback.DeliveryPolicyEvaluationRepository;
import de.tum.cit.aet.hephaestus.practices.feedback.DeliveryPolicySurface;
import de.tum.cit.aet.hephaestus.practices.feedback.Feedback;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackChannel;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackObservationRepository;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackPlacementRepository;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackQueryFilter;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackRepository;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackRepository.OperatorFeedbackRow;
import de.tum.cit.aet.hephaestus.practices.feedback.approval.FeedbackApprovalRepository;
import de.tum.cit.aet.hephaestus.practices.feedback.approval.dto.FeedbackApprovalDTO;
import de.tum.cit.aet.hephaestus.practices.reviewoutput.ReviewArtifactResolver.ArtifactRef;
import de.tum.cit.aet.hephaestus.practices.reviewoutput.dto.ReviewBoundObservationDTO;
import de.tum.cit.aet.hephaestus.practices.reviewoutput.dto.ReviewFeedbackDTO;
import de.tum.cit.aet.hephaestus.practices.reviewoutput.dto.ReviewFeedbackDetailDTO;
import de.tum.cit.aet.hephaestus.practices.reviewoutput.dto.ReviewPlacementDTO;
import de.tum.cit.aet.hephaestus.practices.reviewoutput.dto.ReviewSubjectDTO;
import de.tum.cit.aet.hephaestus.practices.trace.dto.DeliveryPolicyTraceDTO;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
class ReviewFeedbackQueryService {

    private final FeedbackRepository feedbackRepository;
    private final FeedbackObservationRepository feedbackObservationRepository;
    private final FeedbackPlacementRepository feedbackPlacementRepository;
    private final ReviewSubjectResolver subjectResolver;
    private final ReviewArtifactResolver artifactResolver;
    private final FeedbackApprovalRepository approvalRepository;
    private final DeliveryPolicyEvaluationRepository policyEvaluations;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public Page<ReviewFeedbackDTO> list(Long workspaceId, FeedbackQueryFilter filter, Pageable pageable) {
        Page<OperatorFeedbackRow> rows = feedbackRepository.findForWorkspace(workspaceId, filter, pageable);
        List<Long> userIds = new ArrayList<>(rows.getNumberOfElements() * 2);
        for (OperatorFeedbackRow row : rows) {
            userIds.add(row.getRecipientUserId());
            userIds.add(row.getAboutUserId());
        }
        Map<Long, ReviewSubjectDTO> subjects = subjectResolver.resolve(userIds);
        var artifacts = artifactResolver.resolve(
                workspaceId,
                rows.getContent().stream()
                        .filter(row -> row.getArtifactKind() != null && row.getArtifactId() != null)
                        .map(row -> new ArtifactRef(
                                row.getAgentJobId(), ArtifactKind.of(row.getArtifactKind()), row.getArtifactId()))
                        .toList());
        return rows.map(row -> {
            var artifact = row.getArtifactKind() == null || row.getArtifactId() == null
                    ? null
                    : artifacts.get(new ArtifactRef(
                            row.getAgentJobId(), ArtifactKind.of(row.getArtifactKind()), row.getArtifactId()));
            return ReviewFeedbackDTO.from(row, artifact, subjects);
        });
    }

    @Transactional(readOnly = true)
    public ReviewFeedbackDetailDTO get(Long workspaceId, UUID feedbackId) {
        Feedback feedback = feedbackRepository
                .findByIdAndWorkspaceId(feedbackId, workspaceId)
                .orElseThrow(() -> new EntityNotFoundException("Feedback", feedbackId.toString()));
        List<ReviewBoundObservationDTO> observations =
                feedbackObservationRepository.findBoundObservations(workspaceId, feedbackId).stream()
                        .map(ReviewBoundObservationDTO::from)
                        .toList();
        List<ReviewPlacementDTO> placements =
                feedbackPlacementRepository.findByFeedbackIdInDisplayOrder(feedbackId).stream()
                        .map(ReviewPlacementDTO::from)
                        .toList();
        Map<Long, ReviewSubjectDTO> subjects =
                subjectResolver.resolve(List.of(feedback.getRecipientUserId(), feedback.getAboutUserId()));
        var artifactKey = feedback.getArtifactKind() == null || feedback.getArtifactId() == null
                ? null
                : new ArtifactRef(feedback.getAgentJobId(), feedback.getArtifactKind(), feedback.getArtifactId());
        var artifact = artifactKey == null
                ? null
                : artifactResolver.resolve(workspaceId, List.of(artifactKey)).get(artifactKey);
        var evaluations =
                policyEvaluations.findByWorkspaceIdAndFeedbackIdOrderByEvaluatedAtAsc(workspaceId, feedbackId);
        if (evaluations.isEmpty()) {
            evaluations =
                    policyEvaluations.findByWorkspaceIdAndAgentJobIdAndFeedbackIdIsNullAndSurfaceOrderByEvaluatedAtAsc(
                            workspaceId, feedback.getAgentJobId(), surfaceFor(feedback.getChannel()));
        }
        List<DeliveryPolicyTraceDTO> deliveryPolicy = evaluations.stream()
                .map(evaluation -> DeliveryPolicyTraceDTO.from(evaluation, objectMapper))
                .toList();
        FeedbackApprovalDTO approval = approvalRepository
                .findByFeedbackIdAndWorkspaceId(feedbackId, workspaceId)
                .map(FeedbackApprovalDTO::from)
                .orElse(null);
        return ReviewFeedbackDetailDTO.from(
                feedback,
                artifact,
                subjects.get(feedback.getRecipientUserId()),
                subjects.get(feedback.getAboutUserId()),
                observations,
                placements,
                approval,
                deliveryPolicy,
                bodyVisibleToOperator(feedback));
    }

    private static DeliveryPolicySurface surfaceFor(FeedbackChannel channel) {
        return switch (channel) {
            case IN_CONTEXT -> DeliveryPolicySurface.ARTIFACT;
            case IN_APP -> DeliveryPolicySurface.IN_APP;
            case IN_CHAT -> DeliveryPolicySurface.CONVERSATION;
        };
    }

    private static boolean bodyVisibleToOperator(Feedback feedback) {
        return (feedback.getChannel() != FeedbackChannel.IN_APP && feedback.getChannel() != FeedbackChannel.IN_CHAT);
    }
}
