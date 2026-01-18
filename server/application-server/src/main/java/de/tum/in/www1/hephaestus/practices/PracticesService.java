package de.tum.in.www1.hephaestus.practices;

import de.tum.in.www1.hephaestus.core.exception.AccessForbiddenException;
import de.tum.in.www1.hephaestus.core.exception.EntityNotFoundException;
import de.tum.in.www1.hephaestus.gitprovider.issue.Issue;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequest;
import de.tum.in.www1.hephaestus.gitprovider.user.User;
import de.tum.in.www1.hephaestus.gitprovider.user.UserRepository;
import de.tum.in.www1.hephaestus.practices.detection.BadPracticeDetectionRepository;
import de.tum.in.www1.hephaestus.practices.detection.PullRequestBadPracticeDetector;
import de.tum.in.www1.hephaestus.practices.detection.PullRequestBadPracticeRepository;
import de.tum.in.www1.hephaestus.practices.model.BadPracticeDetection;
import de.tum.in.www1.hephaestus.practices.model.DetectionResult;
import de.tum.in.www1.hephaestus.practices.model.PullRequestBadPractice;
import de.tum.in.www1.hephaestus.practices.model.PullRequestBadPracticeDTO;
import de.tum.in.www1.hephaestus.practices.model.PullRequestBadPracticeState;
import de.tum.in.www1.hephaestus.practices.model.PullRequestWithBadPracticesDTO;
import de.tum.in.www1.hephaestus.workspace.Workspace;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for managing bad practice detection and resolution.
 *
 * <p>This service encapsulates all business logic for the practices bounded context,
 * including:
 * <ul>
 *   <li>Retrieving bad practices for users and pull requests</li>
 *   <li>Triggering detection for users and individual PRs</li>
 *   <li>Resolving bad practices with user state updates</li>
 *   <li>Authorization checks for practice operations</li>
 * </ul>
 */
@Service
public class PracticesService {

    private final PullRequestBadPracticeDetector detector;
    private final PullRequestBadPracticeRepository badPracticeRepository;
    private final BadPracticeDetectionRepository detectionRepository;
    private final UserRepository userRepository;
    private final PracticesPullRequestQueryRepository practicesPullRequestQueryRepository;

    public PracticesService(
        PullRequestBadPracticeDetector detector,
        PullRequestBadPracticeRepository badPracticeRepository,
        BadPracticeDetectionRepository detectionRepository,
        UserRepository userRepository,
        PracticesPullRequestQueryRepository practicesPullRequestQueryRepository
    ) {
        this.detector = detector;
        this.badPracticeRepository = badPracticeRepository;
        this.detectionRepository = detectionRepository;
        this.userRepository = userRepository;
        this.practicesPullRequestQueryRepository = practicesPullRequestQueryRepository;
    }

    /**
     * Retrieves all bad practices for pull requests assigned to a user in a workspace.
     *
     * @param workspace the workspace to scope the query
     * @param login the user's login
     * @return list of pull requests with their associated bad practices
     */
    @Transactional(readOnly = true)
    public List<PullRequestWithBadPracticesDTO> getBadPracticesForUser(Workspace workspace, String login) {
        List<PullRequest> pullRequests = practicesPullRequestQueryRepository.findAssignedByLoginAndStates(
            login,
            Set.of(Issue.State.OPEN),
            workspace.getId()
        );

        if (pullRequests.isEmpty()) {
            return List.of();
        }

        // Batch fetch all detections and bad practices to avoid N+1 queries
        Set<Long> prIds = pullRequests.stream()
            .map(PullRequest::getId)
            .collect(Collectors.toSet());

        Map<Long, BadPracticeDetection> detectionsMap =
            detectionRepository.findMostRecentByPullRequestIdsAsMap(prIds);
        Map<Long, List<PullRequestBadPractice>> badPracticesMap =
            badPracticeRepository.findByPullRequestIdsAsMap(prIds);

        return pullRequests.stream()
            .map(pr -> buildPullRequestWithBadPractices(pr, detectionsMap, badPracticesMap))
            .collect(Collectors.toList());
    }

    /**
     * Retrieves bad practices for a specific pull request.
     *
     * @param workspace the workspace to scope the query
     * @param pullRequestId the ID of the pull request
     * @return the pull request with its associated bad practices
     * @throws EntityNotFoundException if the pull request is not found or not in workspace
     */
    @Transactional(readOnly = true)
    public PullRequestWithBadPracticesDTO getBadPracticesForPullRequest(Workspace workspace, Long pullRequestId) {
        PullRequest pr = requirePullRequestInWorkspace(pullRequestId, workspace);
        return buildPullRequestWithBadPractices(pr);
    }

    /**
     * Retrieves a specific bad practice by ID.
     *
     * @param workspace the workspace to scope the query
     * @param badPracticeId the ID of the bad practice
     * @return the bad practice DTO
     * @throws EntityNotFoundException if the bad practice is not found or not in workspace
     */
    @Transactional(readOnly = true)
    public PullRequestBadPracticeDTO getBadPractice(Workspace workspace, Long badPracticeId) {
        PullRequestBadPractice badPractice = requireBadPracticeInWorkspace(badPracticeId, workspace);
        return PullRequestBadPracticeDTO.fromPullRequestBadPractice(badPractice);
    }

    /**
     * Triggers bad practice detection for all pull requests assigned to a user.
     *
     * @param workspace the workspace to scope the detection
     * @param login the user's login (must match the current user)
     * @return the aggregate detection result
     * @throws AccessForbiddenException if the current user does not match the target login
     */
    @Transactional
    public DetectionResult detectForUser(Workspace workspace, String login) {
        User currentUser = requireCurrentUser();
        requireSameUser(currentUser, login);

        return detector.detectForUser(workspace.getId(), login);
    }

    /**
     * Triggers bad practice detection for a specific pull request.
     *
     * @param workspace the workspace to scope the detection
     * @param pullRequestId the ID of the pull request
     * @return the detection result
     * @throws EntityNotFoundException if the pull request is not found or not in workspace
     * @throws AccessForbiddenException if the current user is not an assignee
     */
    @Transactional
    public DetectionResult detectForPullRequest(Workspace workspace, Long pullRequestId) {
        User currentUser = requireCurrentUser();
        PullRequest pullRequest = requirePullRequestInWorkspace(pullRequestId, workspace);
        requireAssignee(pullRequest, currentUser);

        return detector.detectAndSyncBadPractices(pullRequest);
    }

    /**
     * Resolves a bad practice by updating its user state.
     *
     * @param workspace the workspace to scope the operation
     * @param badPracticeId the ID of the bad practice
     * @param state the new state (must be FIXED, WONT_FIX, or WRONG)
     * @throws EntityNotFoundException if the bad practice is not found or not in workspace
     * @throws AccessForbiddenException if the current user is not an assignee of the PR
     * @throws IllegalArgumentException if the state is not a valid resolve state
     */
    @Transactional
    public void resolveBadPractice(Workspace workspace, Long badPracticeId, PullRequestBadPracticeState state) {
        User currentUser = requireCurrentUser();
        PullRequestBadPractice badPractice = requireBadPracticeInWorkspace(badPracticeId, workspace);
        requireAssignee(badPractice.getPullRequest(), currentUser);
        requireValidResolveState(state);

        badPractice.setUserState(state);
        badPracticeRepository.save(badPractice);
    }

    /**
     * Gets a bad practice entity for feedback submission.
     * Validates the current user is an assignee of the associated pull request.
     *
     * @param workspace the workspace to scope the operation
     * @param badPracticeId the ID of the bad practice
     * @return the bad practice entity
     * @throws EntityNotFoundException if the bad practice is not found or not in workspace
     * @throws AccessForbiddenException if the current user is not an assignee
     */
    @Transactional(readOnly = true)
    public PullRequestBadPractice getBadPracticeForFeedback(Workspace workspace, Long badPracticeId) {
        User currentUser = requireCurrentUser();
        PullRequestBadPractice badPractice = requireBadPracticeInWorkspace(badPracticeId, workspace);
        requireAssignee(badPractice.getPullRequest(), currentUser);
        return badPractice;
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Private helper methods
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Builds a DTO for a single pull request by fetching its detection and bad practices.
     * Used for single PR lookups where batch fetching is not needed.
     */
    private PullRequestWithBadPracticesDTO buildPullRequestWithBadPractices(PullRequest pr) {
        BadPracticeDetection lastDetection = detectionRepository.findMostRecentByPullRequestId(pr.getId());
        List<PullRequestBadPractice> allBadPractices = badPracticeRepository.findByPullRequestId(pr.getId());
        return buildPullRequestWithBadPracticesDTO(pr, lastDetection, allBadPractices);
    }

    /**
     * Builds a DTO for a pull request using pre-fetched detection and bad practices maps.
     * Used for batch operations to avoid N+1 queries.
     */
    private PullRequestWithBadPracticesDTO buildPullRequestWithBadPractices(
        PullRequest pr,
        Map<Long, BadPracticeDetection> detectionsMap,
        Map<Long, List<PullRequestBadPractice>> badPracticesMap
    ) {
        BadPracticeDetection lastDetection = detectionsMap.get(pr.getId());
        List<PullRequestBadPractice> allBadPractices = badPracticesMap.getOrDefault(pr.getId(), Collections.emptyList());
        return buildPullRequestWithBadPracticesDTO(pr, lastDetection, allBadPractices);
    }

    /**
     * Core logic for building the DTO from a pull request, its detection, and bad practices.
     */
    private PullRequestWithBadPracticesDTO buildPullRequestWithBadPracticesDTO(
        PullRequest pr,
        BadPracticeDetection lastDetection,
        List<PullRequestBadPractice> allBadPractices
    ) {
        List<PullRequestBadPracticeDTO> badPractices = lastDetection == null
            ? List.of()
            : lastDetection
                  .getBadPractices()
                  .stream()
                  .map(PullRequestBadPracticeDTO::fromPullRequestBadPractice)
                  .toList();

        List<String> badPracticeTitles = badPractices.stream().map(PullRequestBadPracticeDTO::title).toList();

        List<PullRequestBadPracticeDTO> oldBadPractices = allBadPractices
            .stream()
            .filter(badPractice -> !badPracticeTitles.contains(badPractice.getTitle()))
            .map(PullRequestBadPracticeDTO::fromPullRequestBadPractice)
            .toList();

        String summary = lastDetection != null ? lastDetection.getSummary() : "";
        return PullRequestWithBadPracticesDTO.fromPullRequest(pr, summary, badPractices, oldBadPractices);
    }

    private User requireCurrentUser() {
        return userRepository
            .getCurrentUser()
            .orElseThrow(() -> new AccessForbiddenException("User not authenticated"));
    }

    private void requireSameUser(User currentUser, String login) {
        if (!currentUser.getLogin().equals(login)) {
            throw new AccessForbiddenException("Cannot access practices for another user");
        }
    }

    private void requireAssignee(PullRequest pullRequest, User user) {
        if (!pullRequest.getAssignees().contains(user)) {
            throw new AccessForbiddenException("User is not an assignee of this pull request");
        }
    }

    private void requireValidResolveState(PullRequestBadPracticeState state) {
        if (
            state != PullRequestBadPracticeState.FIXED &&
            state != PullRequestBadPracticeState.WONT_FIX &&
            state != PullRequestBadPracticeState.WRONG
        ) {
            throw new IllegalArgumentException("Invalid state: must be FIXED, WONT_FIX, or WRONG");
        }
    }

    private PullRequest requirePullRequestInWorkspace(Long pullRequestId, Workspace workspace) {
        PullRequest pr = practicesPullRequestQueryRepository
            .findById(pullRequestId)
            .orElseThrow(() -> new EntityNotFoundException("PullRequest", pullRequestId));
        if (!belongsToWorkspace(pr, workspace)) {
            throw new EntityNotFoundException("PullRequest", pullRequestId);
        }
        return pr;
    }

    private PullRequestBadPractice requireBadPracticeInWorkspace(Long badPracticeId, Workspace workspace) {
        PullRequestBadPractice bp = badPracticeRepository
            .findById(badPracticeId)
            .orElseThrow(() -> new EntityNotFoundException("BadPractice", badPracticeId));
        if (!belongsToWorkspace(bp.getPullRequest(), workspace)) {
            throw new EntityNotFoundException("BadPractice", badPracticeId);
        }
        return bp;
    }

    private boolean belongsToWorkspace(PullRequest pullRequest, Workspace workspace) {
        if (pullRequest == null || pullRequest.getRepository() == null) {
            return false;
        }
        var prOrg = pullRequest.getRepository().getOrganization();
        var wsOrg = workspace.getOrganization();
        // If both have organizations, compare by ID; otherwise check if PR org is null (allowed for user workspaces)
        if (prOrg != null && wsOrg != null) {
            return prOrg.getId().equals(wsOrg.getId());
        }
        return prOrg == null && wsOrg == null;
    }
}
