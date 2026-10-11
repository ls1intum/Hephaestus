package de.tum.cit.aet.hephaestus.agent.job;

import de.tum.cit.aet.hephaestus.agent.handler.ReviewOutputService;
import de.tum.cit.aet.hephaestus.agent.handler.spi.JobDeliveryException;
import de.tum.cit.aet.hephaestus.agent.job.AgentJobRepository.ReviewRunListFactsRow;
import de.tum.cit.aet.hephaestus.agent.job.AgentJobRepository.ReviewRunSummaryRow;
import de.tum.cit.aet.hephaestus.integration.scm.domain.signal.ScmSignals;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackRepository;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackRepository.ReviewFeedbackCounts;
import de.tum.cit.aet.hephaestus.practices.model.ObservationOrigin;
import de.tum.cit.aet.hephaestus.practices.observation.ObservationRepository;
import de.tum.cit.aet.hephaestus.practices.observation.ObservationRepository.ReviewObservationCounts;
import de.tum.cit.aet.hephaestus.practices.observation.ObservationRepository.StandingRunObservationRow;
import de.tum.cit.aet.hephaestus.practices.observation.RunPracticeOutcomes;
import de.tum.cit.aet.hephaestus.practices.reviewoutput.ReviewSubjectResolver;
import de.tum.cit.aet.hephaestus.practices.reviewoutput.dto.ReviewFeedbackCountsDTO;
import de.tum.cit.aet.hephaestus.practices.reviewoutput.dto.ReviewSubjectDTO;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewObservationCountsDTO;
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
    private final ObservationRepository observationRepository;
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
        Map<UUID, ReviewObservationCounts> observationCounts =
                observationRepository.summarizeReviewObservations(workspaceId, jobIds).stream()
                        .collect(Collectors.toMap(ReviewObservationCounts::getJobId, Function.identity()));
        Map<UUID, ReviewFeedbackCounts> feedbackCounts =
                feedbackRepository.summarizeReviewFeedback(workspaceId, jobIds).stream()
                        .collect(Collectors.toMap(ReviewFeedbackCounts::getJobId, Function.identity()));
        Map<UUID, ReviewRunListFactsRow> listFacts =
                agentJobRepository
                        .findReviewRunListFacts(workspaceId, jobIds, ScmSignals.PULL_REQUEST_MERGED.value())
                        .stream()
                        .collect(Collectors.toMap(ReviewRunListFactsRow::getId, Function.identity()));
        // An observation an admin marked incorrect counts as never made: it names no developer and no verdict here.
        Map<UUID, List<StandingRunObservationRow>> observations =
                observationRepository.findStandingRunObservations(workspaceId, jobIds).stream()
                        .collect(Collectors.groupingBy(StandingRunObservationRow::getAgentJobId));
        Map<UUID, List<Long>> developerIds = reviews.stream()
                .collect(Collectors.toMap(
                        ReviewRunSummaryRow::getId,
                        review -> developerIds(review, observations.getOrDefault(review.getId(), List.of()))));
        Map<Long, ReviewSubjectDTO> developers = subjectResolver.resolve(
                developerIds.values().stream().flatMap(List::stream).toList());
        return reviews.map(review -> {
            UUID id = review.getId();
            ReviewRunListFactsRow facts = listFacts.get(id);
            RunPracticeOutcomes outcomes =
                    RunPracticeOutcomes.ofCounted(observations.getOrDefault(id, List.of()).stream()
                            .map(StandingRunObservationRow::counted)
                            .toList());
            return new ReviewRunSummaryDTO(
                    id,
                    review.getStatus(),
                    ReviewRunOutcome.fromRecordedValue(review.getReviewOutcome()),
                    review.getDeliveryStatus(),
                    ReviewRunTargetDTO.from(Objects.requireNonNull(targets.get(id))),
                    review.getCreatedAt(),
                    ReviewObservationCountsDTO.from(observationCounts.get(id)),
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
     * Everyone the run observed, in the order first observed; before it observed anyone, the person its metadata
     * names, so a running review already says whose work it reads.
     */
    private static List<Long> developerIds(ReviewRunSummaryRow review, List<StandingRunObservationRow> observations) {
        Set<Long> observed = observations.stream()
                .sorted(Comparator.comparing(StandingRunObservationRow::getObservedAt)
                        .thenComparing(StandingRunObservationRow::getId))
                .map(StandingRunObservationRow::getAboutUserId)
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
    private static @Nullable ObservationOrigin origin(@Nullable JsonNode metadata) {
        try {
            return ReviewOutputService.originOf(metadata);
        } catch (JobDeliveryException unknown) {
            return null;
        }
    }
}
