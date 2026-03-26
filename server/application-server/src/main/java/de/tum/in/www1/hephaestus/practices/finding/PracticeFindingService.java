package de.tum.in.www1.hephaestus.practices.finding;

import de.tum.in.www1.hephaestus.core.exception.EntityNotFoundException;
import de.tum.in.www1.hephaestus.gitprovider.user.User;
import de.tum.in.www1.hephaestus.gitprovider.user.UserRepository;
import de.tum.in.www1.hephaestus.practices.finding.dto.ContributorPracticeSummaryProjection;
import de.tum.in.www1.hephaestus.practices.model.PracticeFinding;
import de.tum.in.www1.hephaestus.practices.model.PracticeFindingTargetType;
import de.tum.in.www1.hephaestus.practices.model.Verdict;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for reading practice findings scoped to the authenticated contributor.
 *
 * <p>All methods resolve the current user from the security context via
 * {@link UserRepository#getCurrentUser()}. If the user is not yet synced as a
 * contributor (e.g., first login before any PR activity), list/summary endpoints
 * return empty results rather than failing.
 *
 * <p>For single-finding access, contributor ownership is enforced in SQL — a
 * non-owner receives 404 (not 403) to avoid leaking finding existence.
 */
@Service
@RequiredArgsConstructor
public class PracticeFindingService {

    private final PracticeFindingRepository practiceFindingRepository;
    private final UserRepository userRepository;

    /**
     * Paginated findings for the current user in a workspace, with optional filters.
     *
     * @return empty page if user is not a synced contributor
     */
    @Transactional(readOnly = true)
    public Page<PracticeFinding> getFindings(
        Long workspaceId,
        String practiceSlug,
        Verdict verdict,
        Pageable pageable
    ) {
        Optional<User> currentUser = userRepository.getCurrentUser();
        if (currentUser.isEmpty()) {
            return Page.empty(pageable);
        }
        return practiceFindingRepository.findByContributorAndWorkspace(
            currentUser.get().getId(),
            workspaceId,
            practiceSlug,
            verdict,
            pageable
        );
    }

    /**
     * Per-practice summary for the current user in a workspace.
     *
     * @return empty list if user is not a synced contributor
     */
    @Transactional(readOnly = true)
    public List<ContributorPracticeSummaryProjection> getSummary(Long workspaceId) {
        Optional<User> currentUser = userRepository.getCurrentUser();
        if (currentUser.isEmpty()) {
            return List.of();
        }
        return practiceFindingRepository.findSummaryByContributorAndWorkspace(currentUser.get().getId(), workspaceId);
    }

    /**
     * Single finding detail. Ownership is enforced in the SQL query itself —
     * a finding belonging to another contributor simply won't be returned.
     *
     * @return the finding if it exists and belongs to the current user
     * @throws EntityNotFoundException if no user, or finding not found/not owned
     */
    @Transactional(readOnly = true)
    public PracticeFinding getFinding(Long workspaceId, UUID findingId) {
        Optional<User> currentUser = userRepository.getCurrentUser();
        if (currentUser.isEmpty()) {
            throw new EntityNotFoundException("PracticeFinding", findingId.toString());
        }
        return practiceFindingRepository
            .findByIdAndContributorAndWorkspace(findingId, currentUser.get().getId(), workspaceId)
            .orElseThrow(() -> new EntityNotFoundException("PracticeFinding", findingId.toString()));
    }

    /**
     * All findings for a specific pull request within a workspace.
     * Any workspace member can view PR findings (not restricted to the PR author).
     */
    @Transactional(readOnly = true)
    public List<PracticeFinding> getFindingsForPullRequest(Long workspaceId, Long pullRequestId) {
        return practiceFindingRepository.findByPullRequestAndWorkspace(
            PracticeFindingTargetType.PULL_REQUEST,
            pullRequestId,
            workspaceId
        );
    }
}
