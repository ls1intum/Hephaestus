package de.tum.cit.aet.hephaestus.agent.job;

import de.tum.cit.aet.hephaestus.agent.handler.ReviewOutputService;
import de.tum.cit.aet.hephaestus.agent.handler.spi.JobDeliveryException;
import de.tum.cit.aet.hephaestus.agent.job.AgentJobRepository.ReviewRunListFactsRow;
import de.tum.cit.aet.hephaestus.agent.job.AgentJobRepository.ReviewRunSummaryRow;
import de.tum.cit.aet.hephaestus.integration.scm.domain.signal.ScmSignals;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackRepository;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackRepository.ReviewFeedbackCounts;
import de.tum.cit.aet.hephaestus.practices.model.ResultOrigin;
import de.tum.cit.aet.hephaestus.practices.result.PracticeResultRepository;
import de.tum.cit.aet.hephaestus.practices.result.PracticeResultRepository.ReviewResultCounts;
import de.tum.cit.aet.hephaestus.practices.result.PracticeResultRepository.StandingRunResultRow;
import de.tum.cit.aet.hephaestus.practices.result.RunPracticeOutcomes;
import de.tum.cit.aet.hephaestus.practices.reviewoutput.ReviewSubjectResolver;
import de.tum.cit.aet.hephaestus.practices.reviewoutput.dto.ReviewFeedbackCountsDTO;
import de.tum.cit.aet.hephaestus.practices.reviewoutput.dto.ReviewSubjectDTO;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewResultCountsDTO;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewRunLookup.Target;
import java.time.Clock;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

@Service
@RequiredArgsConstructor
class ReviewRunSummaryQueryService {

    /** The metadata keys that name the person a run's work is about, in the order they are trusted. */
    private static final List<String> SUBJECT_METADATA_KEYS = List.of("about_user_id", "author_id");

    private final AgentJobRepository agentJobRepository;
    private final PracticeResultRepository practiceResultRepository;
    private final FeedbackRepository feedbackRepository;
    private final ReviewSubjectResolver subjectResolver;
    private final ReviewRunTargets reviewRunTargets;
    private final Clock clock;

    @Transactional(readOnly = true)
    public Page<ReviewRunSummaryDTO> list(Long workspaceId, ReviewRunFilterParams filter, Pageable pageable) {
        Page<ReviewRunSummaryRow> reviews = agentJobRepository.findReviewRunSummaries(
                workspaceId, filter, filter.from(), filter.to(), clock.instant(), pageable);
        if (reviews.isEmpty()) {
            return new PageImpl<>(List.of(), reviews.getPageable(), reviews.getTotalElements());
        }
        var jobIds = reviews.stream().map(ReviewRunSummaryRow::getId).toList();
        Map<UUID, Target> targets = reviewRunTargets.of(workspaceId, reviews.getContent());
        Map<UUID, ReviewResultCounts> practiceResultCounts =
                practiceResultRepository.summarizeReviewResults(workspaceId, jobIds).stream()
                        .collect(Collectors.toMap(ReviewResultCounts::getJobId, Function.identity()));
        Map<UUID, ReviewFeedbackCounts> feedbackCounts =
                feedbackRepository.summarizeReviewFeedback(workspaceId, jobIds).stream()
                        .collect(Collectors.toMap(ReviewFeedbackCounts::getJobId, Function.identity()));
        Map<UUID, ReviewRunListFactsRow> listFacts =
                agentJobRepository
                        .findReviewRunListFacts(workspaceId, jobIds, ScmSignals.PULL_REQUEST_MERGED.value())
                        .stream()
                        .collect(Collectors.toMap(ReviewRunListFactsRow::getId, Function.identity()));
        // A result an admin marked incorrect counts as never made: it names no developer and no verdict here.
        Map<UUID, List<StandingRunResultRow>> standing =
                practiceResultRepository.findStandingRunResults(workspaceId, jobIds).stream()
                        .collect(Collectors.groupingBy(StandingRunResultRow::getAgentJobId));
        Map<UUID, List<Long>> developerIds = reviews.stream()
                .collect(Collectors.toMap(
                        ReviewRunSummaryRow::getId,
                        review -> developerIds(review, standing.getOrDefault(review.getId(), List.of()))));
        Map<Long, ReviewSubjectDTO> developers = subjectResolver.resolve(
                developerIds.values().stream().flatMap(List::stream).toList());
        return reviews.map(review -> {
            UUID id = review.getId();
            ReviewRunListFactsRow facts = listFacts.get(id);
            RunPracticeOutcomes outcomes =
                    RunPracticeOutcomes.ofCounted(standing.getOrDefault(id, List.of()).stream()
                            .map(StandingRunResultRow::counted)
                            .toList());
            return new ReviewRunSummaryDTO(
                    id,
                    review.getStatus(),
                    ReviewRunOutcome.fromRecordedValue(review.getReviewOutcome()),
                    review.getDeliveryStatus(),
                    ReviewRunTargetDTO.from(Objects.requireNonNull(targets.get(id))),
                    review.getCreatedAt(),
                    ReviewResultCountsDTO.from(practiceResultCounts.get(id)),
                    ReviewFeedbackCountsDTO.from(feedbackCounts.get(id)),
                    origin(review.getMetadata()),
                    developerIds.getOrDefault(id, List.of()).stream()
                            .map(developers::get)
                            .filter(Objects::nonNull)
                            .toList(),
                    outcomes.practices(),
                    outcomes.slipped(),
                    facts == null ? null : facts.getEvaluated(),
                    facts == null || facts.getPosition() < 1 ? null : Math.toIntExact(facts.getPosition()),
                    facts == null || facts.getReviewsOnWork() < 1 ? null : Math.toIntExact(facts.getReviewsOnWork()),
                    facts == null ? null : facts.getHoldReason(),
                    facts != null && Boolean.TRUE.equals(facts.getFinishedAfterMerge()),
                    facts != null && Boolean.TRUE.equals(facts.getStartedByMerge()));
        });
    }

    /**
     * Everyone the run recorded a result about, in the order first recorded; before it recorded any, the person its metadata
     * names, so a running review already says whose work it reads.
     */
    private static List<Long> developerIds(ReviewRunSummaryRow review, List<StandingRunResultRow> results) {
        Set<Long> observed = results.stream()
                .sorted(Comparator.comparing(StandingRunResultRow::getObservedAt)
                        .thenComparing(StandingRunResultRow::getId))
                .map(StandingRunResultRow::getAboutUserId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (!observed.isEmpty()) {
            return List.copyOf(observed);
        }
        Long named = namedSubjectId(review.getMetadata());
        return named == null ? List.of() : List.of(named);
    }

    /** The person a run's metadata says its work is about, before the run observed anyone. */
    static @Nullable Long namedSubjectId(@Nullable JsonNode metadata) {
        if (metadata == null) {
            return null;
        }
        return SUBJECT_METADATA_KEYS.stream()
                .map(metadata::path)
                .filter(JsonNode::isIntegralNumber)
                .map(JsonNode::asLong)
                .findFirst()
                .orElse(null);
    }

    /** A list must not fail on one run: an origin this release does not know is left unsaid. */
    private static @Nullable ResultOrigin origin(@Nullable JsonNode metadata) {
        try {
            return ReviewOutputService.originOf(metadata);
        } catch (JobDeliveryException unknown) {
            return null;
        }
    }
}
