package de.tum.cit.aet.hephaestus.practices.reviewoutput;

import de.tum.cit.aet.hephaestus.core.auth.spi.AccountSummaryQuery;
import de.tum.cit.aet.hephaestus.core.auth.spi.AccountSummaryQuery.AccountSummary;
import de.tum.cit.aet.hephaestus.core.exception.EntityNotFoundException;
import de.tum.cit.aet.hephaestus.evidence.SourceUsePurpose;
import de.tum.cit.aet.hephaestus.integration.core.signal.ArtifactKind;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackEvidenceRepository;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackRepository;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackRepository.ResultFeedbackCounts;
import de.tum.cit.aet.hephaestus.practices.model.PracticeResult;
import de.tum.cit.aet.hephaestus.practices.model.PracticeResultInvalidation;
import de.tum.cit.aet.hephaestus.practices.result.ObservationQueryFilter;
import de.tum.cit.aet.hephaestus.practices.result.ObservationRepository;
import de.tum.cit.aet.hephaestus.practices.result.ObservationRepository.OperatorObservationRow;
import de.tum.cit.aet.hephaestus.practices.result.PracticeResultInvalidationRepository;
import de.tum.cit.aet.hephaestus.practices.result.PracticeResultRepository;
import de.tum.cit.aet.hephaestus.practices.result.PracticeResultRepository.OperatorResultRow;
import de.tum.cit.aet.hephaestus.practices.result.ResultQueryFilter;
import de.tum.cit.aet.hephaestus.practices.result.reaction.ReactionRepository;
import de.tum.cit.aet.hephaestus.practices.reviewoutput.dto.FeedbackDisputeDTO;
import de.tum.cit.aet.hephaestus.practices.reviewoutput.dto.PracticeResultInvalidationDTO;
import de.tum.cit.aet.hephaestus.practices.reviewoutput.dto.PracticeReviewObservationRowDTO;
import de.tum.cit.aet.hephaestus.practices.reviewoutput.dto.ReviewBoundFeedbackDTO;
import de.tum.cit.aet.hephaestus.practices.reviewoutput.dto.ReviewResultDTO;
import de.tum.cit.aet.hephaestus.practices.reviewoutput.dto.ReviewResultDetailDTO;
import de.tum.cit.aet.hephaestus.practices.reviewoutput.dto.ReviewSubjectDTO;
import de.tum.cit.aet.hephaestus.practices.spi.EvidenceAuthorization;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewRunLookup;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewRunLookup.Target;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewedWorkLabels;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewedWorkRefDTO;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
class ReviewResultQueryService {

    private final PracticeResultRepository practiceResultRepository;
    private final FeedbackEvidenceRepository feedbackEvidenceRepository;
    private final FeedbackRepository feedbackRepository;
    private final ReviewSubjectResolver subjectResolver;
    private final ReviewRunLookup reviewRunLookup;
    private final EvidenceAuthorization evidenceAuthorization;
    private final PracticeResultInvalidationRepository invalidationRepository;
    private final AccountSummaryQuery accountSummaryQuery;
    private final ReactionRepository reactionRepository;
    private final ObservationRepository observationRepository;

    @Transactional(readOnly = true)
    public Page<ReviewResultDTO> list(
            Long workspaceId, ResultQueryFilter filter, ReviewResultSort sort, Pageable pageable) {
        Page<OperatorResultRow> rows = practiceResultRepository.findForWorkspace(
                workspaceId, filter, sort == ReviewResultSort.ACTIONABILITY, pageable);
        Map<Long, ReviewSubjectDTO> subjects = subjectResolver.resolve(rows.getContent().stream()
                .map(OperatorResultRow::getAboutUserId)
                .toList());
        Map<UUID, ResultFeedbackCounts> feedbackCounts = rows.isEmpty()
                ? Map.of()
                : feedbackRepository
                        .summarizeFeedbackByResult(
                                workspaceId,
                                rows.getContent().stream()
                                        .map(OperatorResultRow::getId)
                                        .toList())
                        .stream()
                        .collect(Collectors.toMap(ResultFeedbackCounts::getPracticeResultId, Function.identity()));
        Map<UUID, Target> targets = reviewRunLookup.findTargets(
                workspaceId,
                rows.getContent().stream().map(OperatorResultRow::getAgentJobId).toList());
        return rows.map(row -> ReviewResultDTO.from(
                row,
                feedbackCounts.get(row.getId()),
                ReviewedWorkLabels.ref(
                        ArtifactKind.of(row.getArtifactKind()), row.getArtifactId(), targets.get(row.getAgentJobId())),
                subjects));
    }

    /**
     * One page of the workspace's observations. Each row carries its summary and place only where its result's
     * detail would show them ({@link EvidenceAuthorization} for operator review); elsewhere it is flagged instead.
     */
    @Transactional(readOnly = true)
    public Page<PracticeReviewObservationRowDTO> listObservations(
            Long workspaceId, ResultQueryFilter resultFilter, ObservationQueryFilter filter, Pageable pageable) {
        Page<OperatorObservationRow> rows =
                observationRepository.findForWorkspace(workspaceId, resultFilter, filter, pageable);
        Set<UUID> resultIds = rows.getContent().stream()
                .map(OperatorObservationRow::getPracticeResultId)
                .collect(Collectors.toSet());
        Set<UUID> shown = resultIds.isEmpty()
                ? Set.of()
                : evidenceAuthorization.permitsAll(
                        workspaceId,
                        practiceResultRepository.findForEvidenceAuthorization(resultIds, workspaceId),
                        SourceUsePurpose.OPERATOR_EVIDENCE_REVIEW);
        Map<Long, ReviewSubjectDTO> subjects = subjectResolver.resolve(rows.getContent().stream()
                .map(OperatorObservationRow::getAboutUserId)
                .toList());
        Map<UUID, Target> targets = reviewRunLookup.findTargets(
                workspaceId,
                rows.getContent().stream()
                        .map(OperatorObservationRow::getAgentJobId)
                        .toList());
        return rows.map(row -> PracticeReviewObservationRowDTO.from(
                row,
                shown.contains(row.getPracticeResultId()),
                ReviewedWorkLabels.ref(
                        ArtifactKind.of(row.getArtifactKind()), row.getArtifactId(), targets.get(row.getAgentJobId())),
                subjects));
    }

    @Transactional(readOnly = true)
    public ReviewResultDetailDTO get(Long workspaceId, UUID practiceResultId) {
        PracticeResult practiceResult = practiceResultRepository
                .findByIdAndWorkspaceId(practiceResultId, workspaceId)
                .orElseThrow(() -> new EntityNotFoundException("PracticeResult", practiceResultId.toString()));
        List<ReviewBoundFeedbackDTO> feedback =
                feedbackEvidenceRepository.findBoundFeedbackUnits(workspaceId, practiceResultId).stream()
                        .map(ReviewBoundFeedbackDTO::from)
                        .toList();
        ReviewSubjectDTO subject = subjectResolver
                .resolve(List.of(practiceResult.getAboutUserId()))
                .get(practiceResult.getAboutUserId());
        ReviewedWorkRefDTO reviewedWork = ReviewedWorkLabels.ref(
                practiceResult.getArtifactKind(),
                practiceResult.getArtifactId(),
                reviewRunLookup
                        .findTargets(workspaceId, List.of(practiceResult.getAgentJobId()))
                        .get(practiceResult.getAgentJobId()));
        boolean includeEvidence =
                evidenceAuthorization.permits(workspaceId, practiceResult, SourceUsePurpose.OPERATOR_EVIDENCE_REVIEW);
        List<PracticeResultInvalidation> history = invalidationRepository.findHistory(workspaceId, practiceResultId);
        Map<Long, AccountSummary> accounts = accountSummaryQuery.findAllByIds(history.stream()
                .flatMap(invalidation ->
                        Stream.of(invalidation.getInvalidatedByAccountId(), invalidation.getRestoredByAccountId()))
                .filter(Objects::nonNull)
                .collect(Collectors.toSet()));
        List<PracticeResultInvalidationDTO> invalidations = history.stream()
                .map(invalidation -> PracticeResultInvalidationDTO.from(invalidation, accounts))
                .toList();
        List<FeedbackDisputeDTO> disputes =
                reactionRepository.findStandingDisputesOfResult(workspaceId, practiceResultId).stream()
                        .map(FeedbackDisputeDTO::from)
                        .toList();
        return ReviewResultDetailDTO.from(
                practiceResult,
                reviewedWork,
                subject,
                feedback,
                invalidations,
                disputes,
                includeEvidence,
                observationRepository.findByPracticeResultIds(workspaceId, List.of(practiceResultId)));
    }
}
