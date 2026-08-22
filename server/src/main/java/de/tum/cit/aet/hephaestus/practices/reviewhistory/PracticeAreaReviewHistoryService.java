package de.tum.cit.aet.hephaestus.practices.reviewhistory;

import de.tum.cit.aet.hephaestus.integration.core.signal.ArtifactKind;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.UserRepository;
import de.tum.cit.aet.hephaestus.practices.PracticeAreaService;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackObservationRepository;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackObservationRepository.DeliveredFeedbackBinding;
import de.tum.cit.aet.hephaestus.practices.model.ArtifactKinds;
import de.tum.cit.aet.hephaestus.practices.model.Observation;
import de.tum.cit.aet.hephaestus.practices.model.Severity;
import de.tum.cit.aet.hephaestus.practices.observation.ObservationRepository;
import de.tum.cit.aet.hephaestus.practices.observation.ObservationRepository.ReviewHistoryRunRow;
import de.tum.cit.aet.hephaestus.practices.reviewhistory.dto.PracticeAreaReviewArtifactDTO;
import de.tum.cit.aet.hephaestus.practices.reviewhistory.dto.PracticeAreaReviewFindingDTO;
import de.tum.cit.aet.hephaestus.practices.reviewhistory.dto.PracticeAreaReviewMomentDTO;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewRunTargetLookup;
import de.tum.cit.aet.hephaestus.workspace.context.WorkspaceContext;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Builds the learner-facing history at review-run grain instead of leaking raw observation pagination. */
@Service
@RequiredArgsConstructor
public class PracticeAreaReviewHistoryService {

    private final PracticeAreaService practiceAreaService;
    private final ObservationRepository observationRepository;
    private final FeedbackObservationRepository feedbackObservationRepository;
    private final ReviewRunTargetLookup reviewRunTargetLookup;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public Page<PracticeAreaReviewMomentDTO> list(
        WorkspaceContext workspaceContext,
        String areaSlug,
        @Nullable String practiceSlug,
        @Nullable List<ArtifactKind> artifactKinds,
        @Nullable List<Severity> severities,
        Pageable pageable
    ) {
        practiceAreaService.getArea(workspaceContext, areaSlug);
        var currentUser = userRepository.getCurrentUser();
        if (currentUser.isEmpty()) {
            return Page.empty(pageable);
        }

        boolean hasArtifactKinds = artifactKinds != null && !artifactKinds.isEmpty();
        boolean hasSeverities = severities != null && !severities.isEmpty();
        List<ArtifactKind> artifactFilter = hasArtifactKinds ? artifactKinds : List.of(ArtifactKinds.PULL_REQUEST);
        List<Severity> severityFilter = hasSeverities ? severities : List.of(Severity.INFO);

        Page<ReviewHistoryRunRow> runs = observationRepository.findReviewHistoryRuns(
            currentUser.get().getId(),
            workspaceContext.id(),
            areaSlug,
            practiceSlug,
            hasArtifactKinds,
            artifactFilter.stream().map(ArtifactKind::value).toList(),
            hasSeverities,
            severityFilter.stream().map(Enum::name).toList(),
            pageable
        );
        if (runs.isEmpty()) {
            return Page.empty(pageable);
        }

        List<UUID> jobIds = runs.stream().map(ReviewHistoryRunRow::getJobId).toList();
        List<Observation> observations = observationRepository.findReviewHistoryObservationsByJobs(
            jobIds,
            currentUser.get().getId(),
            workspaceContext.id(),
            areaSlug,
            practiceSlug,
            hasArtifactKinds,
            artifactFilter,
            hasSeverities,
            severityFilter
        );
        Map<UUID, List<Observation>> observationsByJob = observations
            .stream()
            .collect(Collectors.groupingBy(Observation::getAgentJobId));
        Map<UUID, DeliveredFeedbackBinding> feedbackByObservation = feedbackByObservation(
            workspaceContext.id(),
            currentUser.get().getId(),
            observations
        );
        Map<UUID, ReviewRunTargetLookup.Target> targets = reviewRunTargetLookup.findByJobIds(
            workspaceContext.id(),
            jobIds
        );

        return runs.map(run ->
            toMoment(run, observationsByJob.getOrDefault(run.getJobId(), List.of()), feedbackByObservation, targets)
        );
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

    private PracticeAreaReviewMomentDTO toMoment(
        ReviewHistoryRunRow run,
        List<Observation> observations,
        Map<UUID, DeliveredFeedbackBinding> feedbackByObservation,
        Map<UUID, ReviewRunTargetLookup.Target> targets
    ) {
        Observation first = observations.getFirst();
        var target = targets.get(run.getJobId());
        PracticeAreaReviewArtifactDTO artifact =
            target == null
                ? PracticeAreaReviewArtifactDTO.fallback(first.getArtifactKind(), first.getArtifactId())
                : PracticeAreaReviewArtifactDTO.from(target, first.getArtifactKind(), first.getArtifactId());
        List<PracticeAreaReviewFindingDTO> findings = observations
            .stream()
            .map(observation -> {
                DeliveredFeedbackBinding binding = feedbackByObservation.get(observation.getId());
                return PracticeAreaReviewFindingDTO.from(
                    observation,
                    binding == null ? null : binding.getFeedbackId(),
                    binding == null ? null : binding.getHelpful()
                );
            })
            .toList();
        return new PracticeAreaReviewMomentDTO(run.getJobId(), run.getReviewedAt(), artifact, findings);
    }
}
