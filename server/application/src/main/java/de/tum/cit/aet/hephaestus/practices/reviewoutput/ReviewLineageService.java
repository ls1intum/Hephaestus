package de.tum.cit.aet.hephaestus.practices.reviewoutput;

import de.tum.cit.aet.hephaestus.core.exception.EntityNotFoundException;
import de.tum.cit.aet.hephaestus.integration.core.signal.ArtifactKind;
import de.tum.cit.aet.hephaestus.integration.core.signal.DiscoveredVia;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackChannel;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackDeliveryState;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackLocation;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackSuppressionReason;
import de.tum.cit.aet.hephaestus.practices.feedback.approval.FeedbackApprovalDecision;
import de.tum.cit.aet.hephaestus.practices.feedback.inapp.InAppFeedbackEvidence;
import de.tum.cit.aet.hephaestus.practices.model.ArtifactKinds;
import de.tum.cit.aet.hephaestus.practices.model.Outcome;
import de.tum.cit.aet.hephaestus.practices.model.Severity;
import de.tum.cit.aet.hephaestus.practices.observation.reaction.ReactionRepository;
import de.tum.cit.aet.hephaestus.practices.observation.reaction.ReactionRepository.AdminVisibleResponseRow;
import de.tum.cit.aet.hephaestus.practices.reviewoutput.ReviewLineageRepository.BindingRow;
import de.tum.cit.aet.hephaestus.practices.reviewoutput.ReviewLineageRepository.FeedbackRow;
import de.tum.cit.aet.hephaestus.practices.reviewoutput.ReviewLineageRepository.MomentRow;
import de.tum.cit.aet.hephaestus.practices.reviewoutput.ReviewLineageRepository.ObservationRow;
import de.tum.cit.aet.hephaestus.practices.reviewoutput.ReviewLineageRepository.StartRow;
import de.tum.cit.aet.hephaestus.practices.reviewoutput.dto.ReviewLineageDTO;
import de.tum.cit.aet.hephaestus.practices.reviewoutput.dto.ReviewLineageFeedbackDTO;
import de.tum.cit.aet.hephaestus.practices.reviewoutput.dto.ReviewLineageMomentDTO;
import de.tum.cit.aet.hephaestus.practices.reviewoutput.dto.ReviewLineageObservationDTO;
import de.tum.cit.aet.hephaestus.practices.reviewoutput.dto.ReviewLineageResponseDTO;
import de.tum.cit.aet.hephaestus.practices.reviewoutput.dto.ReviewLineageRunDTO;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewRunLookup;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewRunLookup.ReviewRunFacts;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewRunLookup.Target;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewedWorkLabels;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewedWorkRefDTO;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * How one observation, piece of feedback, review or piece of work came about, as one bounded chain: work, review,
 * observations, feedback, delivery and the developer's answer. Every list is capped, and the count beside it says how
 * much the cap left out, so a review with many practices or a card bound to many observations still answers in a
 * handful of rows.
 */
@Service
@RequiredArgsConstructor
class ReviewLineageService {

    /** Observations a review or a piece of work lists. */
    static final int OBSERVATION_CAP = 6;

    /** Observations a piece of feedback lists: all it was written from, up to this many. */
    static final int BOUND_OBSERVATION_CAP = 12;

    static final int FEEDBACK_CAP = 6;

    /** Observations, and pieces of feedback, the whole review lists. */
    static final int WHOLE_CAP = 100;

    /** Moments on the work the whole review lists. */
    static final int MOMENT_CAP = 30;

    private final ReviewLineageRepository repository;
    private final ReviewRunLookup reviewRunLookup;
    private final ReactionRepository reactionRepository;
    private final InAppFeedbackEvidence inAppFeedbackEvidence;
    private final Clock clock;

    /**
     * @param whole list every observation and piece of feedback of the observation's review, up to
     *     {@link #WHOLE_CAP} each, and the work's moments, rather than the short chain a side column shows
     */
    @Transactional(readOnly = true)
    public ReviewLineageDTO ofObservation(Long workspaceId, UUID observationId, boolean whole) {
        ObservationRow observation = repository.findObservations(workspaceId, List.of(observationId)).stream()
                .findFirst()
                .orElseThrow(() -> new EntityNotFoundException("Observation", observationId.toString()));
        UUID reviewId = observation.getAgentJobId();
        List<UUID> feedbackIds = repository.findFeedbackIdsOfObservation(workspaceId, observationId);
        if (whole) {
            return wholeReview(
                    workspaceId, reviewId, List.of(), Set.of(observationId), feedbackIds, Set.copyOf(feedbackIds));
        }
        int others = repository.countObservationsOfReview(workspaceId, reviewId) - 1;
        return assemble(
                workspaceId,
                reviewId,
                List.of(observation),
                Set.of(observationId),
                Math.max(others, 0),
                feedbackIds,
                Set.copyOf(feedbackIds),
                FEEDBACK_CAP,
                false);
    }

    /** @param whole as {@link #ofObservation(Long, UUID, boolean)} says */
    @Transactional(readOnly = true)
    public ReviewLineageDTO ofFeedback(Long workspaceId, UUID feedbackId, boolean whole) {
        FeedbackRow feedback = repository.findFeedback(workspaceId, List.of(feedbackId)).stream()
                .findFirst()
                .orElseThrow(() -> new EntityNotFoundException("Feedback", feedbackId.toString()));
        UUID reviewId = feedback.getAgentJobId();
        List<UUID> bound = repository.findBindings(workspaceId, List.of(feedbackId)).stream()
                .map(BindingRow::getObservationId)
                .distinct()
                .toList();
        if (whole) {
            return wholeReview(
                    workspaceId, reviewId, bound, Set.copyOf(bound), List.of(feedbackId), Set.of(feedbackId));
        }
        List<ObservationRow> listed = ordered(
                repository.findObservations(
                        workspaceId, bound.subList(0, Math.min(bound.size(), BOUND_OBSERVATION_CAP))),
                bound);
        // Left out: what the cap cut from the observations behind it, and the review's own observations it was not
        // written from — the siblings the chain collapses.
        Set<UUID> chain = new HashSet<>(bound);
        chain.addAll(repository.findObservationIdsOfReview(workspaceId, reviewId));
        return assemble(
                workspaceId,
                reviewId,
                listed,
                Set.copyOf(bound),
                chain.size() - listed.size(),
                List.of(feedbackId),
                Set.of(feedbackId),
                FEEDBACK_CAP,
                false);
    }

    /** @param whole as {@link #ofObservation(Long, UUID, boolean)} says */
    @Transactional(readOnly = true)
    public ReviewLineageDTO ofReview(Long workspaceId, UUID reviewId, boolean whole) {
        return reviewLineage(workspaceId, reviewId, whole);
    }

    private ReviewLineageDTO reviewLineage(Long workspaceId, UUID reviewId, boolean whole) {
        if (whole) {
            return wholeReview(workspaceId, reviewId, List.of(), Set.of(), List.of(), Set.of());
        }
        List<ObservationRow> listed = repository.findObservationsOfReview(workspaceId, reviewId, OBSERVATION_CAP);
        int total = repository.countObservationsOfReview(workspaceId, reviewId);
        List<UUID> feedbackIds = repository.findFeedbackIdsOfReview(workspaceId, reviewId);
        return assemble(
                workspaceId,
                reviewId,
                listed,
                Set.of(),
                total - listed.size(),
                feedbackIds,
                Set.of(),
                FEEDBACK_CAP,
                false);
    }

    /**
     * Everything one review recorded, for a page that has the room: its observations and the feedback it composed,
     * each up to {@link #WHOLE_CAP}, joined by the records the request names that another review owns — the
     * observations a piece of feedback was written from, the feedback written from an observation. The ones the
     * request names stay in front, so the cap never cuts them.
     */
    private ReviewLineageDTO wholeReview(
            Long workspaceId,
            UUID reviewId,
            List<UUID> namedObservations,
            Set<UUID> focusedObservations,
            List<UUID> namedFeedback,
            Set<UUID> focusedFeedback) {
        List<UUID> reviewObservations = repository.findObservationIdsOfReview(workspaceId, reviewId);
        List<UUID> reviewFeedback = repository.findFeedbackIdsOfReview(workspaceId, reviewId);
        Set<UUID> observationIds = new LinkedHashSet<>(namedObservations);
        observationIds.addAll(focusedObservations);
        List<ObservationRow> ownListed = repository.findObservationsOfReview(workspaceId, reviewId, WHOLE_CAP);
        Set<UUID> allObservations = new HashSet<>(observationIds);
        allObservations.addAll(reviewObservations);
        for (ObservationRow row : ownListed) {
            if (observationIds.size() >= WHOLE_CAP) {
                break;
            }
            observationIds.add(row.getId());
        }
        List<UUID> listedIds = List.copyOf(observationIds);
        List<ObservationRow> listed = listedIds.isEmpty()
                ? List.of()
                : ordered(repository.findObservations(workspaceId, listedIds), listedIds);
        Set<UUID> feedbackIds = new LinkedHashSet<>(namedFeedback);
        feedbackIds.addAll(focusedFeedback);
        feedbackIds.addAll(reviewFeedback);
        return assemble(
                workspaceId,
                reviewId,
                listed,
                focusedObservations,
                allObservations.size() - listed.size(),
                List.copyOf(feedbackIds),
                focusedFeedback,
                WHOLE_CAP,
                true);
    }

    /**
     * The chain of the newest review that recorded anything about the work.
     *
     * @param whole as {@link #ofObservation(Long, UUID, boolean)} says
     */
    @Transactional(readOnly = true)
    public ReviewLineageDTO ofWork(Long workspaceId, ArtifactKind artifactKind, long artifactId, boolean whole) {
        UUID reviewId = repository
                .findNewestReviewOfWork(workspaceId, artifactKind.value(), artifactId)
                .orElseThrow(
                        () -> new EntityNotFoundException("Reviewed work", artifactKind.value() + ":" + artifactId));
        return reviewLineage(workspaceId, reviewId, whole);
    }

    private ReviewLineageDTO assemble(
            Long workspaceId,
            UUID reviewId,
            List<ObservationRow> observations,
            Set<UUID> focusedObservations,
            int moreObservations,
            List<UUID> feedbackIds,
            Set<UUID> focusedFeedback,
            int feedbackCap,
            boolean withMoments) {
        List<UUID> listedFeedbackIds = feedbackIds.subList(0, Math.min(feedbackIds.size(), feedbackCap));
        List<FeedbackRow> feedback =
                listedFeedbackIds.isEmpty() ? List.of() : repository.findFeedback(workspaceId, listedFeedbackIds);
        Map<UUID, List<UUID>> bindings = listedFeedbackIds.isEmpty()
                ? Map.of()
                : repository.findBindings(workspaceId, listedFeedbackIds).stream()
                        .collect(Collectors.groupingBy(
                                BindingRow::getFeedbackId,
                                LinkedHashMap::new,
                                Collectors.mapping(BindingRow::getObservationId, Collectors.toList())));
        Map<UUID, ReviewLineageResponseDTO> responses = responses(workspaceId, feedback);

        ReviewRunFacts facts =
                reviewRunLookup.findFacts(workspaceId, List.of(reviewId)).get(reviewId);
        // A review that recorded nothing still has a chain while its run exists, such as a queued or coalesced one.
        if (facts == null && observations.isEmpty() && feedback.isEmpty()) {
            throw new EntityNotFoundException("Review", reviewId.toString());
        }
        ReviewLineageRunDTO review = facts == null
                ? null
                : new ReviewLineageRunDTO(
                        reviewId,
                        facts.status(),
                        facts.createdAt(),
                        facts.model(),
                        facts.completedAt(),
                        facts.phase(),
                        facts.startedAt(),
                        facts.holdReason(),
                        facts.conclusion());
        ReviewedWorkRefDTO work = work(observations, feedback, facts);
        StartRow start = work == null ? null : start(workspaceId, work, reviewId);

        return new ReviewLineageDTO(
                work,
                review,
                observations.stream()
                        .map(row -> observation(row, focusedObservations.contains(row.getId())))
                        .toList(),
                Math.max(moreObservations, 0),
                feedback.stream()
                        .map(row -> feedback(
                                row,
                                bindings.getOrDefault(row.getId(), List.of()),
                                focusedFeedback.contains(row.getId()),
                                responses.get(row.getId())))
                        .toList(),
                feedbackIds.size() - listedFeedbackIds.size(),
                work == null ? null : mergedAt(workspaceId, work),
                start == null ? null : trigger(start),
                work == null || !withMoments ? List.of() : moments(workspaceId, work, reviewId, start));
    }

    /**
     * The work's newest moments, oldest first. A moment recorded twice — once per revision, or found by an event and
     * again by a sync — is one moment, and it started the review if either record did. Of the moments that name the
     * review, only the first started it, as {@link #trigger} reads it; a later one was folded into it.
     */
    private List<ReviewLineageMomentDTO> moments(
            Long workspaceId, ReviewedWorkRefDTO work, UUID reviewId, @Nullable StartRow startRow) {
        Long id = artifactId(work);
        if (id == null) {
            return List.of();
        }
        UUID start = startRow == null ? null : startRow.getId();
        Map<String, ReviewLineageMomentDTO> moments = new LinkedHashMap<>();
        for (MomentRow row : repository.findMoments(workspaceId, work.kind().value(), id, MOMENT_CAP * 2)) {
            ReviewTrigger kind =
                    ReviewTrigger.moment(row.getSignalName(), DiscoveredVia.valueOf(row.getDiscoveredVia()));
            if (kind == null) {
                continue;
            }
            boolean started = row.getId().equals(start);
            boolean joined = !started && reviewId.equals(row.getJobId());
            moments.merge(
                    kind + "@" + row.getOccurredAt(),
                    new ReviewLineageMomentDTO(kind, row.getOccurredAt(), started, joined),
                    (a, b) -> new ReviewLineageMomentDTO(
                            a.kind(),
                            a.occurredAt(),
                            a.startedReview() || b.startedReview(),
                            !(a.startedReview() || b.startedReview()) && (a.joinedReview() || b.joinedReview())));
        }
        List<ReviewLineageMomentDTO> newest =
                new ArrayList<>(moments.values().stream().limit(MOMENT_CAP).toList());
        Collections.reverse(newest);
        // The moment that started the review is older than the window when many came after it; it stays, as the
        // oldest moment shown, so the timeline never contradicts the trigger.
        ReviewTrigger startKind = startRow == null
                ? null
                : ReviewTrigger.moment(startRow.getSignalName(), DiscoveredVia.valueOf(startRow.getDiscoveredVia()));
        if (startRow != null && startKind != null && newest.stream().noneMatch(ReviewLineageMomentDTO::startedReview)) {
            if (newest.size() >= MOMENT_CAP) {
                newest.removeFirst();
            }
            newest.addFirst(new ReviewLineageMomentDTO(startKind, startRow.getOccurredAt(), true, false));
        }
        return newest;
    }

    /** When a pull request or merge request was merged; null for other work. */
    private @Nullable Instant mergedAt(Long workspaceId, ReviewedWorkRefDTO work) {
        Long id = artifactId(work);
        if (id == null || !ArtifactKinds.PULL_REQUEST.equals(work.kind())) {
            return null;
        }
        return repository.findMergedAt(workspaceId, id).orElse(null);
    }

    /** The first moment on the work that names the review, the one that started it; null when none does. */
    private @Nullable StartRow start(Long workspaceId, ReviewedWorkRefDTO work, UUID reviewId) {
        Long id = artifactId(work);
        if (id == null) {
            return null;
        }
        return repository
                .findStart(workspaceId, work.kind().value(), id, reviewId)
                .orElse(null);
    }

    private static @Nullable ReviewTrigger trigger(StartRow start) {
        return ReviewTrigger.of(start.getSignalName(), DiscoveredVia.valueOf(start.getDiscoveredVia()));
    }

    /** The work's id within its kind; every kind a review names is keyed by a number. */
    private static @Nullable Long artifactId(ReviewedWorkRefDTO work) {
        try {
            return Long.valueOf(work.id());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    /**
     * Whether the developer answered each piece of feedback and whether that answer disputes it, and, for a card on
     * their practice pages, whether their later work resolved it. Only what an admin may read is queried: the
     * dispute's explanation is written to the admins, while the rating, any other resolution and the note beside it
     * stay private to the developer and never leave SQL ({@link ReactionRepository#findAdminVisibleResponses}).
     *
     * <p>{@code respondedAt} is set for an answer that is only a rating too, so admins see that and when the developer
     * rated, not how. This is intended for now and kept as an open product decision.
     */
    private Map<UUID, ReviewLineageResponseDTO> responses(Long workspaceId, Collection<FeedbackRow> feedback) {
        if (feedback.isEmpty()) {
            return Map.of();
        }
        Map<UUID, AdminVisibleResponseRow> answers =
                reactionRepository
                        .findAdminVisibleResponses(
                                workspaceId,
                                feedback.stream().map(FeedbackRow::getId).toList())
                        .stream()
                        .collect(Collectors.toMap(
                                AdminVisibleResponseRow::getFeedbackId, Function.identity(), (a, b) -> a));
        Map<UUID, Instant> resolvedByWork = resolvedByWork(workspaceId, feedback);
        Map<UUID, ReviewLineageResponseDTO> responses = new LinkedHashMap<>();
        for (FeedbackRow row : feedback) {
            AdminVisibleResponseRow answer = answers.get(row.getId());
            Instant resolvedAt = resolvedByWork.get(row.getId());
            if (answer == null && resolvedAt == null) {
                continue;
            }
            boolean disputed = answer != null && Boolean.TRUE.equals(answer.getDisputed());
            responses.put(
                    row.getId(),
                    new ReviewLineageResponseDTO(
                            answer == null ? null : answer.getRespondedAt(),
                            disputed,
                            answer == null ? null : answer.getDisputeExplanation(),
                            resolvedAt));
        }
        return responses;
    }

    private Map<UUID, Instant> resolvedByWork(Long workspaceId, Collection<FeedbackRow> feedback) {
        return inAppFeedbackEvidence.resolvedByWorkAt(
                workspaceId,
                repository.findAllByWorkspaceIdAndIdIn(
                        workspaceId, feedback.stream().map(FeedbackRow::getId).toList()),
                clock.instant());
    }

    /**
     * The work the records name, labelled the way the review names it now. The records carry the work's identity
     * themselves; the review's own target only names it, and is the fallback when the chain lists no record.
     */
    private static @Nullable ReviewedWorkRefDTO work(
            List<ObservationRow> observations, List<FeedbackRow> feedback, @Nullable ReviewRunFacts facts) {
        Target target = facts == null ? null : facts.target();
        if (!observations.isEmpty()) {
            ObservationRow first = observations.getFirst();
            return ReviewedWorkLabels.ref(ArtifactKind.of(first.getArtifactKind()), first.getArtifactId(), target);
        }
        for (FeedbackRow row : feedback) {
            if (row.getArtifactKind() != null && row.getArtifactId() != null) {
                return ReviewedWorkLabels.ref(ArtifactKind.of(row.getArtifactKind()), row.getArtifactId(), target);
            }
        }
        return target == null ? null : ReviewedWorkLabels.refOrNull(target.type(), target.id(), target);
    }

    /** Rows in the order of {@code ids}, which a SQL {@code IN} does not keep. */
    private static List<ObservationRow> ordered(List<ObservationRow> rows, List<UUID> ids) {
        List<ObservationRow> sorted = new ArrayList<>(rows);
        sorted.sort(Comparator.comparingInt(row -> ids.indexOf(row.getId())));
        return sorted;
    }

    private static ReviewLineageObservationDTO observation(ObservationRow row, boolean focus) {
        return new ReviewLineageObservationDTO(
                row.getId(),
                row.getAgentJobId(),
                row.getSummary(),
                row.getPracticeSlug(),
                row.getPracticeName(),
                Outcome.valueOf(row.getOutcome()),
                row.getSeverity() == null ? null : Severity.valueOf(row.getSeverity()),
                row.getObservedAt(),
                focus,
                row.getMarkedIncorrectAt() != null,
                Math.toIntExact(row.getFeedbackCount()),
                row.getMarkedIncorrectAt(),
                Math.toIntExact(row.getOwnFeedbackCount()));
    }

    private static ReviewLineageFeedbackDTO feedback(
            FeedbackRow row, List<UUID> observationIds, boolean focus, @Nullable ReviewLineageResponseDTO response) {
        return new ReviewLineageFeedbackDTO(
                row.getId(),
                row.getAgentJobId(),
                FeedbackChannel.valueOf(row.getChannel()),
                FeedbackDeliveryState.valueOf(row.getDeliveryState()),
                row.getSuppressionReason() == null
                        ? null
                        : FeedbackSuppressionReason.valueOf(row.getSuppressionReason()),
                row.getCreatedAt(),
                row.getDeliveredAt(),
                Math.toIntExact(row.getPlacementCount()),
                observationIds,
                row.getReplacesId(),
                row.getReplacedById(),
                row.getReplacedAt(),
                focus,
                response,
                FeedbackLocation.valueOf(row.getLocation()),
                row.getDecision() == null ? null : FeedbackApprovalDecision.valueOf(row.getDecision()),
                row.getDecidedAt(),
                row.getWithdrawnAt());
    }
}
