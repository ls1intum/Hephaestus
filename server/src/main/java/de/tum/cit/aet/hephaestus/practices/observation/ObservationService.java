package de.tum.cit.aet.hephaestus.practices.observation;

import de.tum.cit.aet.hephaestus.core.exception.EntityNotFoundException;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.User;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.UserRepository;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackObservationRepository;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackObservationRepository.ObservationAdviceBody;
import de.tum.cit.aet.hephaestus.practices.model.Observation;
import de.tum.cit.aet.hephaestus.practices.model.Presence;
import de.tum.cit.aet.hephaestus.practices.model.Severity;
import de.tum.cit.aet.hephaestus.practices.model.WorkArtifact;
import de.tum.cit.aet.hephaestus.practices.observation.dto.DeveloperPracticeSummaryProjection;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewRunTargetLookup;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reads practice findings scoped to the authenticated developer.
 *
 * <p>List and summary endpoints return empty results when the current user has not yet been synced as a
 * developer. Single-finding access enforces ownership in SQL and returns 404 for non-owners so finding
 * existence is not leaked.
 */
@Service
@RequiredArgsConstructor
public class ObservationService {

    private final ObservationRepository observationRepository;
    private final FeedbackObservationRepository feedbackObservationRepository;
    private final UserRepository userRepository;
    private final ReviewRunTargetLookup reviewRunTargetLookup;

    /** Feed ordering: by observation time or by severity (direction applies to both). */
    public enum ObservationSort {
        DATE,
        SEVERITY,
    }

    /** Paginated findings for the current user in a workspace, with optional filters. */
    @Transactional(readOnly = true)
    public Page<Observation> getObservations(
        Long workspaceId,
        String practiceSlug,
        String areaSlug,
        Presence presence,
        @Nullable List<WorkArtifact> artifactTypes,
        @Nullable List<Severity> severities,
        boolean displayableOnly,
        ObservationSort sort,
        boolean mostSevereFirst,
        Pageable pageable
    ) {
        Optional<User> currentUser = userRepository.getCurrentUser();
        if (currentUser.isEmpty()) {
            return Page.empty(pageable);
        }
        // An IN () over an empty list is invalid SQL. The flags disable empty filters, while the placeholder
        // values keep the query parseable.
        boolean hasArtifactTypes = artifactTypes != null && !artifactTypes.isEmpty();
        boolean hasSeverities = severities != null && !severities.isEmpty();
        if (sort == ObservationSort.SEVERITY) {
            return observationRepository.findByAboutUserAndWorkspaceSeverityFirst(
                currentUser.get().getId(),
                workspaceId,
                practiceSlug,
                areaSlug,
                presence,
                hasArtifactTypes,
                hasArtifactTypes ? artifactTypes : List.of(WorkArtifact.PULL_REQUEST),
                hasSeverities,
                hasSeverities ? severities : List.of(Severity.INFO),
                displayableOnly,
                mostSevereFirst ? 1 : -1,
                pageable
            );
        }
        return observationRepository.findByAboutUserAndWorkspace(
            currentUser.get().getId(),
            workspaceId,
            practiceSlug,
            areaSlug,
            presence,
            hasArtifactTypes,
            hasArtifactTypes ? artifactTypes : List.of(WorkArtifact.PULL_REQUEST),
            hasSeverities,
            hasSeverities ? severities : List.of(Severity.INFO),
            displayableOnly,
            pageable
        );
    }

    /**
     * Link to the reviewed artifact behind an observation. Empty when the run target is unknown, for example
     * after the artifact was deleted.
     */
    @Transactional(readOnly = true)
    public Optional<String> getArtifactUrl(Long workspaceId, Observation observation) {
        return Optional.ofNullable(
            reviewRunTargetLookup
                .findByJobIds(workspaceId, List.of(observation.getAgentJobId()))
                .get(observation.getAgentJobId())
        ).map(ReviewRunTargetLookup.Target::url);
    }

    /** Per-practice observation counts for the current user in a workspace. */
    @Transactional(readOnly = true)
    public List<DeveloperPracticeSummaryProjection> getSummary(Long workspaceId) {
        Optional<User> currentUser = userRepository.getCurrentUser();
        if (currentUser.isEmpty()) {
            return List.of();
        }
        return observationRepository.findSummaryByDeveloperAndWorkspace(currentUser.get().getId(), workspaceId);
    }

    /**
     * Single finding detail. Ownership is enforced in the SQL query itself, so a finding belonging to another
     * developer is not returned.
     */
    @Transactional(readOnly = true)
    public Observation getObservation(Long workspaceId, UUID observationId) {
        Optional<User> currentUser = userRepository.getCurrentUser();
        if (currentUser.isEmpty()) {
            throw new EntityNotFoundException("Observation", observationId.toString());
        }
        return observationRepository
            .findByIdAndDeveloperAndWorkspace(observationId, currentUser.get().getId(), workspaceId)
            .orElseThrow(() -> new EntityNotFoundException("Observation", observationId.toString()));
    }

    /** Delivered feedback body for a single observation, if the observation was delivered. */
    @Transactional(readOnly = true)
    public Optional<String> getDeliveredGuidance(UUID observationId) {
        return Optional.ofNullable(deliveredGuidanceByObservation(Set.of(observationId)).get(observationId));
    }

    private Map<UUID, String> deliveredGuidanceByObservation(Set<UUID> observationIds) {
        if (observationIds.isEmpty()) {
            return Map.of();
        }
        return feedbackObservationRepository
            .findLatestAdviceBodiesByObservationIds(observationIds)
            .stream()
            .collect(Collectors.toMap(ObservationAdviceBody::getObservationId, ObservationAdviceBody::getBody));
    }

    /** All findings for a pull request in a workspace; any workspace member may view them. */
    @Transactional(readOnly = true)
    public List<Observation> getObservationsForPullRequest(Long workspaceId, Long pullRequestId) {
        return observationRepository.findByPullRequestAndWorkspace(
            WorkArtifact.PULL_REQUEST,
            pullRequestId,
            workspaceId
        );
    }
}
