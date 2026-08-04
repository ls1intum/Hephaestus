package de.tum.cit.aet.hephaestus.agent.job;

import de.tum.cit.aet.hephaestus.agent.config.AgentPurpose;
import de.tum.cit.aet.hephaestus.agent.job.AgentJobRepository.ReviewRunSummaryRow;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackRepository;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackRepository.ReviewFeedbackCounts;
import de.tum.cit.aet.hephaestus.practices.observation.ObservationRepository;
import de.tum.cit.aet.hephaestus.practices.observation.ObservationRepository.ReviewFindingCounts;
import java.util.Map;
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
class ReviewRunSummaryQueryService {

    private final AgentJobRepository agentJobRepository;
    private final ObservationRepository observationRepository;
    private final FeedbackRepository feedbackRepository;

    @Transactional(readOnly = true)
    Page<ReviewRunSummaryDTO> list(Long workspaceId, AgentJobStatus status, Pageable pageable) {
        Page<ReviewRunSummaryRow> reviews =
            status == null
                ? agentJobRepository.findReviewRunSummaries(workspaceId, AgentPurpose.PRACTICE_REVIEW, pageable)
                : agentJobRepository.findReviewRunSummaries(
                      workspaceId,
                      AgentPurpose.PRACTICE_REVIEW,
                      status,
                      pageable
                  );
        if (reviews.isEmpty()) {
            return reviews.map(this::withoutCounts);
        }
        var jobIds = reviews.stream().map(ReviewRunSummaryRow::getId).toList();
        Map<UUID, ReviewFindingCounts> findingCounts = observationRepository
            .summarizeReviewFindings(workspaceId, jobIds)
            .stream()
            .collect(Collectors.toMap(ReviewFindingCounts::getJobId, Function.identity()));
        Map<UUID, ReviewFeedbackCounts> feedbackCounts = feedbackRepository
            .summarizeReviewFeedback(workspaceId, jobIds)
            .stream()
            .collect(Collectors.toMap(ReviewFeedbackCounts::getJobId, Function.identity()));
        return reviews.map(review ->
            from(review, findingCounts.get(review.getId()), feedbackCounts.get(review.getId()))
        );
    }

    private ReviewRunSummaryDTO from(
        ReviewRunSummaryRow review,
        ReviewFindingCounts findingCounts,
        ReviewFeedbackCounts feedbackCounts
    ) {
        return ReviewRunSummaryDTO.from(
            review,
            findingCounts == null
                ? ReviewFindingCountsDTO.empty()
                : new ReviewFindingCountsDTO(
                      findingCounts.getStrengths(),
                      findingCounts.getProblems(),
                      findingCounts.getNotApplicable()
                  ),
            feedbackCounts == null
                ? ReviewFeedbackCountsDTO.empty()
                : new ReviewFeedbackCountsDTO(
                      feedbackCounts.getPrepared(),
                      feedbackCounts.getDelivered(),
                      feedbackCounts.getSuperseded(),
                      feedbackCounts.getSuppressed(),
                      feedbackCounts.getFailed()
                  )
        );
    }

    private ReviewRunSummaryDTO withoutCounts(ReviewRunSummaryRow review) {
        return ReviewRunSummaryDTO.from(review, ReviewFindingCountsDTO.empty(), ReviewFeedbackCountsDTO.empty());
    }
}
