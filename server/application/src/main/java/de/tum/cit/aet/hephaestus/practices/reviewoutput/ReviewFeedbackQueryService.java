package de.tum.cit.aet.hephaestus.practices.reviewoutput;

import de.tum.cit.aet.hephaestus.core.auth.spi.AccountSummaryQuery;
import de.tum.cit.aet.hephaestus.core.auth.spi.AccountSummaryQuery.AccountSummary;
import de.tum.cit.aet.hephaestus.core.exception.EntityNotFoundException;
import de.tum.cit.aet.hephaestus.integration.core.signal.ArtifactKind;
import de.tum.cit.aet.hephaestus.practices.feedback.DeliveryPolicyEvaluationRepository;
import de.tum.cit.aet.hephaestus.practices.feedback.Feedback;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackChannel;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackDeliveryState;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackDispatchRepository;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackDispatchState;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackEvidenceRepository;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackLocation;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackPlacementRepository;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackQueryFilter;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackRepository;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackRepository.OperatorFeedbackRow;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackWithdrawal;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackWithdrawalRepository;
import de.tum.cit.aet.hephaestus.practices.feedback.approval.FeedbackApproval;
import de.tum.cit.aet.hephaestus.practices.feedback.approval.FeedbackApprovalRepository;
import de.tum.cit.aet.hephaestus.practices.feedback.approval.dto.FeedbackApprovalDTO;
import de.tum.cit.aet.hephaestus.practices.result.reaction.ReactionRepository;
import de.tum.cit.aet.hephaestus.practices.reviewoutput.dto.FeedbackDisputeDTO;
import de.tum.cit.aet.hephaestus.practices.reviewoutput.dto.FeedbackWithdrawalDTO;
import de.tum.cit.aet.hephaestus.practices.reviewoutput.dto.ReviewBoundResultDTO;
import de.tum.cit.aet.hephaestus.practices.reviewoutput.dto.ReviewFeedbackCountsDTO;
import de.tum.cit.aet.hephaestus.practices.reviewoutput.dto.ReviewFeedbackDTO;
import de.tum.cit.aet.hephaestus.practices.reviewoutput.dto.ReviewFeedbackDetailDTO;
import de.tum.cit.aet.hephaestus.practices.reviewoutput.dto.ReviewFeedbackDispatchDTO;
import de.tum.cit.aet.hephaestus.practices.reviewoutput.dto.ReviewFeedbackLocationCountsDTO;
import de.tum.cit.aet.hephaestus.practices.reviewoutput.dto.ReviewFeedbackReasonCountDTO;
import de.tum.cit.aet.hephaestus.practices.reviewoutput.dto.ReviewPlacementDTO;
import de.tum.cit.aet.hephaestus.practices.reviewoutput.dto.ReviewSubjectDTO;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewRunLookup;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewRunLookup.Target;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewedWorkLabels;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewedWorkRefDTO;
import de.tum.cit.aet.hephaestus.practices.trace.dto.DeliveryPolicyTraceDTO;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
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
    private final FeedbackEvidenceRepository feedbackEvidenceRepository;
    private final FeedbackPlacementRepository feedbackPlacementRepository;
    private final FeedbackDispatchRepository dispatchRepository;
    private final ReviewSubjectResolver subjectResolver;
    private final ReviewRunLookup reviewRunLookup;
    private final FeedbackApprovalRepository approvalRepository;
    private final DeliveryPolicyEvaluationRepository policyEvaluations;
    private final FeedbackWithdrawalRepository withdrawalRepository;
    private final AccountSummaryQuery accountSummaryQuery;
    private final ReactionRepository reactionRepository;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public Page<ReviewFeedbackDTO> list(
            Long workspaceId, FeedbackQueryFilter filter, ReviewFeedbackSort sort, Pageable pageable) {
        Page<OperatorFeedbackRow> rows =
                feedbackRepository.findForWorkspace(workspaceId, filter, sort == ReviewFeedbackSort.OLDEST, pageable);
        List<Long> userIds = new ArrayList<>(rows.getNumberOfElements() * 2);
        for (OperatorFeedbackRow row : rows) {
            userIds.add(row.getRecipientUserId());
            userIds.add(row.getAboutUserId());
        }
        Map<Long, ReviewSubjectDTO> subjects = subjectResolver.resolve(userIds);
        Map<UUID, Target> targets = reviewRunLookup.findTargets(
                workspaceId,
                rows.getContent().stream()
                        .map(OperatorFeedbackRow::getAgentJobId)
                        .toList());
        return rows.map(row -> {
            String artifactKind = row.getArtifactKind();
            ReviewedWorkRefDTO reviewedWork = ReviewedWorkLabels.refOrNull(
                    artifactKind == null ? null : ArtifactKind.of(artifactKind),
                    row.getArtifactId(),
                    targets.get(row.getAgentJobId()));
            return ReviewFeedbackDTO.from(row, reviewedWork, subjects);
        });
    }

    /**
     * The rows {@link #list} would return under {@code filter}, counted by location and delivery state, in
     * {@link FeedbackLocation} order. A location with no feedback is left out.
     */
    @Transactional(readOnly = true)
    public List<ReviewFeedbackLocationCountsDTO> countByLocation(Long workspaceId, FeedbackQueryFilter filter) {
        return feedbackRepository.summarizeForWorkspaceByLocation(workspaceId, filter).stream()
                .map(row -> new ReviewFeedbackLocationCountsDTO(row.getLocation(), ReviewFeedbackCountsDTO.from(row)))
                .sorted(Comparator.comparing(ReviewFeedbackLocationCountsDTO::location))
                .toList();
    }

    /**
     * The rows {@link #list} would return under {@code filter} that carry a suppression reason, counted by reason, in
     * {@link FeedbackSuppressionReason} order, then the withheld rows without a reason under a null reason. A reason
     * with no feedback is left out.
     */
    @Transactional(readOnly = true)
    public List<ReviewFeedbackReasonCountDTO> countByReason(Long workspaceId, FeedbackQueryFilter filter) {
        return feedbackRepository.summarizeForWorkspaceByReason(workspaceId, filter).stream()
                .map(row -> new ReviewFeedbackReasonCountDTO(row.getReason(), row.getCount()))
                .sorted(Comparator.comparing(
                        ReviewFeedbackReasonCountDTO::reason, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    /**
     * A decision on a feedback proposal with the admin who made it named, as the feedback detail names them, so the
     * decision reads the same from every endpoint that returns it.
     */
    public FeedbackApprovalDTO approval(FeedbackApproval decided) {
        Long actorAccountId = decided.getActorAccountId();
        return approval(
                decided, actorAccountId == null ? Map.of() : accountSummaryQuery.findAllByIds(Set.of(actorAccountId)));
    }

    private static FeedbackApprovalDTO approval(FeedbackApproval decided, Map<Long, AccountSummary> accounts) {
        AccountSummary actor = decided.getActorAccountId() == null ? null : accounts.get(decided.getActorAccountId());
        return FeedbackApprovalDTO.from(decided, actor == null ? null : actor.displayName());
    }

    /**
     * The delivery checks a review ran for itself rather than for one piece of feedback, oldest first. A
     * review of another workspace has none here.
     */
    @Transactional(readOnly = true)
    public List<DeliveryPolicyTraceDTO> reviewDeliveryChecks(Long workspaceId, UUID agentJobId) {
        return policyEvaluations
                .findByWorkspaceIdAndAgentJobIdAndFeedbackIdIsNullOrderByEvaluatedAtAsc(workspaceId, agentJobId)
                .stream()
                .map(evaluation -> DeliveryPolicyTraceDTO.from(evaluation, objectMapper))
                .toList();
    }

    @Transactional(readOnly = true)
    public ReviewFeedbackDetailDTO get(Long workspaceId, UUID feedbackId) {
        Feedback feedback = feedbackRepository
                .findByIdAndWorkspaceId(feedbackId, workspaceId)
                .orElseThrow(() -> new EntityNotFoundException("Feedback", feedbackId.toString()));
        List<ReviewBoundResultDTO> practiceResults =
                feedbackEvidenceRepository.findBoundResults(workspaceId, feedbackId).stream()
                        .map(ReviewBoundResultDTO::from)
                        .toList();
        List<ReviewPlacementDTO> placements =
                feedbackPlacementRepository.findByFeedbackIdInDisplayOrder(feedbackId).stream()
                        .map(ReviewPlacementDTO::from)
                        .toList();
        Map<Long, ReviewSubjectDTO> subjects =
                subjectResolver.resolve(List.of(feedback.getRecipientUserId(), feedback.getAboutUserId()));
        ReviewedWorkRefDTO reviewedWork = ReviewedWorkLabels.refOrNull(
                feedback.getArtifactKind(),
                feedback.getArtifactId(),
                reviewRunLookup
                        .findTargets(workspaceId, List.of(feedback.getAgentJobId()))
                        .get(feedback.getAgentJobId()));
        // Only this feedback's own checks: the checks its review ran for itself belong to the review.
        List<DeliveryPolicyTraceDTO> deliveryPolicy =
                policyEvaluations.findByWorkspaceIdAndFeedbackIdOrderByEvaluatedAtAsc(workspaceId, feedbackId).stream()
                        .map(evaluation -> DeliveryPolicyTraceDTO.from(evaluation, objectMapper))
                        .toList();
        var decision = approvalRepository.findByFeedbackIdAndWorkspaceId(feedbackId, workspaceId);
        List<FeedbackWithdrawal> history = withdrawalRepository.findHistory(workspaceId, feedbackId);
        Map<Long, AccountSummary> accounts = accountSummaryQuery.findAllByIds(Stream.concat(
                        history.stream()
                                .flatMap(withdrawal -> Stream.of(
                                        withdrawal.getWithdrawnByAccountId(), withdrawal.getRestoredByAccountId())),
                        decision.stream().map(FeedbackApproval::getActorAccountId))
                .filter(Objects::nonNull)
                .collect(Collectors.toSet()));
        FeedbackApprovalDTO approval =
                decision.map(decided -> approval(decided, accounts)).orElse(null);
        FeedbackLocation location = feedbackRepository
                .findLocation(workspaceId, feedbackId)
                .map(FeedbackLocation::valueOf)
                .orElseThrow(() -> new EntityNotFoundException("Feedback", feedbackId.toString()));
        return ReviewFeedbackDetailDTO.from(
                feedback,
                location,
                reviewedWork,
                subjects.get(feedback.getRecipientUserId()),
                subjects.get(feedback.getAboutUserId()),
                practiceResults,
                placements,
                dispatches(workspaceId, feedback),
                approval,
                deliveryPolicy,
                history.stream()
                        .map(withdrawal -> FeedbackWithdrawalDTO.from(withdrawal, accounts))
                        .toList(),
                reactionRepository
                        .findStandingDispute(workspaceId, feedbackId)
                        .map(FeedbackDisputeDTO::from)
                        .orElse(null),
                bodyVisibleToOperator(feedback));
    }

    /**
     * The deliveries that carried this feedback to the provider. An approved proposal has its own package; any other
     * feedback on the work went out in its review's package. Withheld feedback was left out of a package its review
     * still sent, so it keeps only a package that was withheld too. Feedback for a practice page or a conversation
     * never goes to a provider.
     */
    private List<ReviewFeedbackDispatchDTO> dispatches(Long workspaceId, Feedback feedback) {
        if (feedback.getChannel() != FeedbackChannel.IN_CONTEXT) {
            return List.of();
        }
        if (!feedback.getProposedPlacements().isEmpty()) {
            return dispatchRepository.findApprovedPackages(workspaceId, feedback.getId()).stream()
                    .map(ReviewFeedbackDispatchDTO::from)
                    .toList();
        }
        boolean withheld = feedback.getDeliveryState() == FeedbackDeliveryState.SUPPRESSED;
        return dispatchRepository.findReviewPackages(workspaceId, feedback.getAgentJobId()).stream()
                .filter(dispatch -> !withheld || dispatch.getState() == FeedbackDispatchState.SUPPRESSED)
                .map(ReviewFeedbackDispatchDTO::from)
                .toList();
    }

    private static boolean bodyVisibleToOperator(Feedback feedback) {
        return (feedback.getChannel() != FeedbackChannel.IN_APP && feedback.getChannel() != FeedbackChannel.IN_CHAT);
    }
}
