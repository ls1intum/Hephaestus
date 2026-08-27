package de.tum.cit.aet.hephaestus.practices.areadetail;

import de.tum.cit.aet.hephaestus.evidence.SourceUsePurpose;
import de.tum.cit.aet.hephaestus.integration.core.signal.ArtifactKind;
import de.tum.cit.aet.hephaestus.practices.PracticeAreaService;
import de.tum.cit.aet.hephaestus.practices.areadetail.dto.PracticeAreaReviewArtifactDTO;
import de.tum.cit.aet.hephaestus.practices.areadetail.dto.PracticeAreaReviewFindingDTO;
import de.tum.cit.aet.hephaestus.practices.areadetail.dto.PracticeAreaReviewMomentDTO;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackObservationRepository;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackObservationRepository.DeliveredFeedbackBinding;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackResolution;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackUsefulness;
import de.tum.cit.aet.hephaestus.practices.model.ArtifactKinds;
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

/** Builds the learner-facing history at review-run grain instead of leaking raw observation pagination. */
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
    public Page<PracticeAreaReviewMomentDTO> list(
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
            return Page.empty(pageable);
        }

        boolean hasArtifactKinds = artifactKinds != null && !artifactKinds.isEmpty();
        boolean hasSeverities = severities != null && !severities.isEmpty();
        // Placeholders, never evaluated: each query gates its IN clause on the matching flag, so with the flag
        // false the predicate short-circuits before the list is read. They are nonetheless non-empty because
        // `findReviewHistoryRuns` is a native query whose collection parameter is expanded into the SQL text —
        // an empty one yields `IN ()`, which Postgres rejects at parse time, before any short-circuit applies.
        // These are NOT defaults: an unfiltered request still returns issues and conversation threads.
        List<ArtifactKind> artifactFilter = hasArtifactKinds
            ? Objects.requireNonNull(artifactKinds)
            : List.of(ArtifactKinds.PULL_REQUEST);
        List<Severity> severityFilter = hasSeverities ? Objects.requireNonNull(severities) : List.of(Severity.INFO);

        Page<ReviewHistoryRunRow> runs = observationRepository.findReviewHistoryRuns(
            currentDeveloperId.get(),
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
            // Empty content, but the count the database made: a page read past the last one still has to say
            // how many runs there are, or a stale deep link reads as "this area was never reviewed".
            return new PageImpl<>(List.of(), pageable, runs.getTotalElements());
        }

        List<UUID> jobIds = runs.stream().map(ReviewHistoryRunRow::getJobId).toList();
        List<Observation> found = observationRepository.findReviewHistoryObservationsByJobs(
            jobIds,
            currentDeveloperId.get(),
            workspaceContext.id(),
            areaSlug,
            practiceSlug,
            hasArtifactKinds,
            artifactFilter,
            hasSeverities,
            severityFilter
        );
        // The same gate the reflection surface applies, for the same reason and with the same purpose: a
        // finding may cite a source this caller is not cleared to be shown, and a claim measured against
        // superseded review rules no longer speaks for the practice. Asked once for the whole page.
        Set<UUID> visible = visibilityPolicy.permitsAll(
            workspaceContext.id(),
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
            workspaceContext.id(),
            currentDeveloperId.get(),
            observations
        );
        Map<UUID, ReviewRunTargetLookup.Target> targets = reviewRunTargetLookup.findByJobIds(
            workspaceContext.id(),
            jobIds
        );

        // A run whose every finding the gate withheld is not an empty moment to render — it is a moment this
        // caller does not get to see, so it leaves the page rather than showing as a review that found
        // nothing. The total still counts it: the gate answers per observation in Java, after the database
        // has already counted rows, and a total that quietly disagreed with the pages would be the worse lie.
        List<PracticeAreaReviewMomentDTO> moments = new ArrayList<>();
        for (ReviewHistoryRunRow run : runs) {
            List<Observation> visibleFindings = observationsByJob.getOrDefault(run.getJobId(), List.of());
            if (!visibleFindings.isEmpty()) {
                moments.add(toMoment(run, visibleFindings, feedbackByObservation, targets));
            }
        }
        return new PageImpl<>(moments, pageable, runs.getTotalElements());
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
                : PracticeAreaReviewArtifactDTO.from(target, first.getArtifactId());
        List<PracticeAreaReviewFindingDTO> findings = observations
            .stream()
            .map(observation -> {
                DeliveredFeedbackBinding binding = feedbackByObservation.get(observation.getId());
                return PracticeAreaReviewFindingDTO.from(
                    observation,
                    binding == null ? null : binding.getFeedbackId(),
                    usefulness(binding),
                    resolution(binding),
                    binding == null ? null : binding.getResponseComment()
                );
            })
            .toList();
        return new PracticeAreaReviewMomentDTO(run.getJobId(), run.getReviewedAt(), artifact, findings);
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
