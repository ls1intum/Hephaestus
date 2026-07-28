package de.tum.cit.aet.hephaestus.practices.observation;

import de.tum.cit.aet.hephaestus.core.exception.EntityNotFoundException;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.User;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.UserRepository;
import de.tum.cit.aet.hephaestus.practices.feedback.DeliveredGuidanceLookup;
import de.tum.cit.aet.hephaestus.practices.model.Observation;
import de.tum.cit.aet.hephaestus.practices.model.Presence;
import de.tum.cit.aet.hephaestus.practices.model.WorkArtifact;
import de.tum.cit.aet.hephaestus.practices.observation.dto.DeveloperPracticeSummaryProjection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for reading the raw observation ledger, scoped to the authenticated developer.
 *
 * <p>The synthesised per-practice report cards, the mentor roster and the workspace-health rollup are NOT
 * here — they live in {@code practices.report}, which owns that read model end to end. This service stays
 * what its name says: observations in, observations out.
 *
 * <p>All methods resolve the current user from the security context via
 * {@link UserRepository#getCurrentUser()}. If the user is not yet synced as a
 * developer (e.g., first login before any PR activity), list/summary endpoints
 * return empty results rather than failing.
 *
 * <p>For single-finding access, developer ownership is enforced in SQL — a
 * non-owner receives 404 (not 403) to avoid leaking finding existence.
 */
@Service
@RequiredArgsConstructor
public class ObservationService {

    private final ObservationRepository observationRepository;
    private final DeliveredGuidanceLookup deliveredGuidanceLookup;
    private final UserRepository userRepository;

    /**
     * Paginated findings for the current user in a workspace, with optional filters.
     *
     * @return empty page if user is not a synced developer
     */
    @Transactional(readOnly = true)
    public Page<Observation> getObservations(
        Long workspaceId,
        String practiceSlug,
        Presence presence,
        Pageable pageable
    ) {
        Optional<User> currentUser = userRepository.getCurrentUser();
        if (currentUser.isEmpty()) {
            return Page.empty(pageable);
        }
        return observationRepository.findByAboutUserAndWorkspace(
            currentUser.get().getId(),
            workspaceId,
            practiceSlug,
            presence,
            pageable
        );
    }

    /**
     * Per-practice summary for the current user in a workspace.
     *
     * @return empty list if user is not a synced developer
     */
    @Transactional(readOnly = true)
    public List<DeveloperPracticeSummaryProjection> getSummary(Long workspaceId) {
        Optional<User> currentUser = userRepository.getCurrentUser();
        if (currentUser.isEmpty()) {
            return List.of();
        }
        return observationRepository.findSummaryByDeveloperAndWorkspace(currentUser.get().getId(), workspaceId);
    }

    /**
     * Single finding detail. Ownership is enforced in the SQL query itself —
     * a finding belonging to another developer simply won't be returned.
     *
     * @return the finding if it exists and belongs to the current user
     * @throws EntityNotFoundException if no user, or finding not found/not owned
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

    /**
     * The delivered feedback body for a single observation — the developer's advice source for the detail
     * view (ADR 0021: advice lives on the delivered {@code Feedback}, not the immutable observation). Empty
     * when the observation was never delivered.
     */
    @Transactional(readOnly = true)
    public Optional<String> getDeliveredGuidance(UUID observationId) {
        return deliveredGuidanceLookup.forObservation(observationId);
    }

    /**
     * All findings for a specific pull request within a workspace.
     * Any workspace member can view PR findings (not restricted to the PR author).
     */
    @Transactional(readOnly = true)
    public List<Observation> getObservationsForPullRequest(Long workspaceId, Long pullRequestId) {
        return observationRepository.findByPullRequestAndWorkspace(
            WorkArtifact.PULL_REQUEST,
            pullRequestId,
            workspaceId
        );
    }
}
