package de.tum.cit.aet.hephaestus.practices.areadetail;

import de.tum.cit.aet.hephaestus.evidence.SourceUsePurpose;
import de.tum.cit.aet.hephaestus.integration.core.signal.ArtifactKind;
import de.tum.cit.aet.hephaestus.practices.PracticeAreaService;
import de.tum.cit.aet.hephaestus.practices.areadetail.dto.PracticeAreaReviewHistoryPageDTO;
import de.tum.cit.aet.hephaestus.practices.areadetail.dto.PracticeAreaReviewObservationDTO;
import de.tum.cit.aet.hephaestus.practices.areadetail.dto.PracticeAreaReviewRunDTO;
import de.tum.cit.aet.hephaestus.practices.areadetail.dto.PracticeAreaReviewedWorkDTO;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackObservationRepository;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackObservationRepository.DeliveredFeedbackBinding;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackResolution;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackUsefulness;
import de.tum.cit.aet.hephaestus.practices.model.Observation;
import de.tum.cit.aet.hephaestus.practices.model.Severity;
import de.tum.cit.aet.hephaestus.practices.observation.ObservationRepository;
import de.tum.cit.aet.hephaestus.practices.observation.ObservationRepository.ReviewHistoryRunRow;
import de.tum.cit.aet.hephaestus.practices.observation.ObservationVisibilityPolicy;
import de.tum.cit.aet.hephaestus.practices.spi.CurrentDeveloperLookup;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewRunTargetLookup;
import de.tum.cit.aet.hephaestus.workspace.context.WorkspaceContext;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Builds the developer-facing history at review-run grain instead of leaking raw observation pagination. */
@Service
@RequiredArgsConstructor
public class PracticeAreaReviewHistoryService {

    private final PracticeAreaService practiceAreaService;
    private final ObservationRepository observationRepository;
    private final FeedbackObservationRepository feedbackObservationRepository;
    private final ReviewRunTargetLookup reviewRunTargetLookup;
    private final CurrentDeveloperLookup currentDeveloperLookup;
    private final ObservationVisibilityPolicy visibilityPolicy;

    @Transactional(readOnly = true)
    public PracticeAreaReviewHistoryPageDTO list(
        WorkspaceContext workspaceContext,
        String areaSlug,
        @Nullable String practiceSlug,
        @Nullable List<ArtifactKind> artifactKinds,
        @Nullable List<Severity> severities,
        Pageable pageable
    ) {
        practiceAreaService.getArea(workspaceContext, areaSlug);
        var currentDeveloperId = currentDeveloperLookup.currentDeveloperId();
        if (currentDeveloperId.isEmpty()) {
            return new PracticeAreaReviewHistoryPageDTO(
                List.of(),
                pageable.getPageNumber(),
                pageable.getPageSize(),
                false
            );
        }

        String artifactFilter =
            artifactKinds == null || artifactKinds.isEmpty()
                ? null
                : artifactKinds.stream().map(ArtifactKind::value).collect(Collectors.joining(","));
        String severityFilter =
            severities == null || severities.isEmpty()
                ? null
                : severities.stream().map(Enum::name).collect(Collectors.joining(","));

        int first = Math.multiplyExact(pageable.getPageNumber(), pageable.getPageSize());
        int required = Math.addExact(first, pageable.getPageSize() + 1);
        List<PracticeAreaReviewRunDTO> visibleRuns = new ArrayList<>(required);
        int candidatePage = 0;
        boolean moreCandidates;
        do {
            Slice<ReviewHistoryRunRow> runs = observationRepository.findReviewHistoryRuns(
                currentDeveloperId.get(),
                workspaceContext.id(),
                areaSlug,
                practiceSlug,
                artifactFilter,
                severityFilter,
                PageRequest.of(candidatePage++, Math.max(pageable.getPageSize(), 50))
            );
            visibleRuns.addAll(
                toVisibleRuns(workspaceContext.id(), currentDeveloperId.get(), runs.getContent(), areaSlug)
            );
            moreCandidates = runs.hasNext();
        } while (visibleRuns.size() < required && moreCandidates);

        int end = Math.min(first + pageable.getPageSize(), visibleRuns.size());
        List<PracticeAreaReviewRunDTO> content =
            first >= visibleRuns.size() ? List.of() : List.copyOf(visibleRuns.subList(first, end));
        return new PracticeAreaReviewHistoryPageDTO(
            content,
            pageable.getPageNumber(),
            pageable.getPageSize(),
            visibleRuns.size() > end
        );
    }

    private List<PracticeAreaReviewRunDTO> toVisibleRuns(
        long workspaceId,
        long developerId,
        List<ReviewHistoryRunRow> runs,
        String areaSlug
    ) {
        if (runs.isEmpty()) {
            return List.of();
        }

        List<UUID> jobIds = runs.stream().map(ReviewHistoryRunRow::getJobId).toList();
        List<Observation> found = observationRepository.findReviewHistoryObservationsByJobs(
            jobIds,
            developerId,
            workspaceId,
            areaSlug
        );
        // The same gate the practice standing surface applies, for the same reason and with the same purpose: a
        // observation may cite a source this caller is not cleared to be shown, and a claim measured against
        // superseded review rules no longer speaks for the practice. Asked once for the whole page.
        Set<UUID> visible = visibilityPolicy.permitsAll(
            workspaceId,
            found,
            SourceUsePurpose.PRACTICE_FEEDBACK_DELIVERY
        );
        List<Observation> observations = found
            .stream()
            .filter(row -> visible.contains(row.getId()))
            .toList();
        Map<UUID, List<Observation>> observationsByJob = observations
            .stream()
            .collect(Collectors.groupingBy(Observation::getAgentJobId));
        Map<UUID, DeliveredFeedbackBinding> feedbackByObservation = feedbackByObservation(
            workspaceId,
            developerId,
            observations
        );
        Map<UUID, ReviewRunTargetLookup.Target> targets = reviewRunTargetLookup.findByJobIds(workspaceId, jobIds);

        List<PracticeAreaReviewRunDTO> reviewRuns = new ArrayList<>();
        for (ReviewHistoryRunRow run : runs) {
            List<Observation> visibleObservations = observationsByJob.getOrDefault(run.getJobId(), List.of());
            if (!visibleObservations.isEmpty()) {
                reviewRuns.add(toReviewRun(run, visibleObservations, feedbackByObservation, targets));
            }
        }
        return reviewRuns;
    }

    private Map<UUID, DeliveredFeedbackBinding> feedbackByObservation(
        Long workspaceId,
        Long recipientUserId,
        Collection<Observation> observations
    ) {
        if (observations.isEmpty()) {
            return Map.of();
        }
        return feedbackObservationRepository
            .findDeliveredFeedbackBindings(
                workspaceId,
                recipientUserId,
                observations.stream().map(Observation::getId).toList()
            )
            .stream()
            .collect(Collectors.toMap(DeliveredFeedbackBinding::getObservationId, Function.identity()));
    }

    private PracticeAreaReviewRunDTO toReviewRun(
        ReviewHistoryRunRow run,
        List<Observation> observations,
        Map<UUID, DeliveredFeedbackBinding> feedbackByObservation,
        Map<UUID, ReviewRunTargetLookup.Target> targets
    ) {
        Observation first = observations.getFirst();
        var target = targets.get(run.getJobId());
        PracticeAreaReviewedWorkDTO reviewedWork =
            target == null
                ? PracticeAreaReviewedWorkDTO.fallback(first.getArtifactKind(), first.getArtifactId())
                : PracticeAreaReviewedWorkDTO.from(target, first.getArtifactId());
        List<PracticeAreaReviewObservationDTO> reviewObservations = observations
            .stream()
            .map(observation -> {
                DeliveredFeedbackBinding binding = feedbackByObservation.get(observation.getId());
                return PracticeAreaReviewObservationDTO.from(
                    observation,
                    binding == null ? null : binding.getFeedbackId(),
                    usefulness(binding),
                    resolution(binding),
                    binding == null ? null : binding.getResponseComment()
                );
            })
            .toList();
        return new PracticeAreaReviewRunDTO(run.getJobId(), run.getReviewedAt(), reviewedWork, reviewObservations);
    }

    private @Nullable FeedbackUsefulness usefulness(@Nullable DeliveredFeedbackBinding binding) {
        return binding == null || binding.getResponseUsefulness() == null
            ? null
            : FeedbackUsefulness.valueOf(binding.getResponseUsefulness());
    }

    private @Nullable FeedbackResolution resolution(@Nullable DeliveredFeedbackBinding binding) {
        return binding == null || binding.getResponseResolution() == null
            ? null
            : FeedbackResolution.valueOf(binding.getResponseResolution());
    }
}
