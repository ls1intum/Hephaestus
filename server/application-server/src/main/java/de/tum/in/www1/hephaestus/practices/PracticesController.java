package de.tum.in.www1.hephaestus.practices;

import de.tum.in.www1.hephaestus.core.exception.AccessForbiddenException;
import de.tum.in.www1.hephaestus.core.exception.EntityNotFoundException;
import de.tum.in.www1.hephaestus.gitprovider.issue.Issue;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequest;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequestRepository;
import de.tum.in.www1.hephaestus.gitprovider.user.User;
import de.tum.in.www1.hephaestus.gitprovider.user.UserRepository;
import de.tum.in.www1.hephaestus.practices.detection.BadPracticeDetectionRepository;
import de.tum.in.www1.hephaestus.practices.detection.PullRequestBadPracticeDetector;
import de.tum.in.www1.hephaestus.practices.detection.PullRequestBadPracticeRepository;
import de.tum.in.www1.hephaestus.practices.feedback.BadPracticeFeedbackService;
import de.tum.in.www1.hephaestus.practices.model.BadPracticeDetection;
import de.tum.in.www1.hephaestus.practices.model.BadPracticeFeedbackDTO;
import de.tum.in.www1.hephaestus.practices.model.DetectionResult;
import de.tum.in.www1.hephaestus.practices.model.PullRequestBadPractice;
import de.tum.in.www1.hephaestus.practices.model.PullRequestBadPracticeDTO;
import de.tum.in.www1.hephaestus.practices.model.PullRequestBadPracticeState;
import de.tum.in.www1.hephaestus.workspace.Workspace;
import de.tum.in.www1.hephaestus.workspace.context.WorkspaceContext;
import de.tum.in.www1.hephaestus.workspace.context.WorkspaceContextResolver;
import de.tum.in.www1.hephaestus.workspace.context.WorkspaceScopedController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Controller for code practices detection and resolution.
 *
 * <p>This controller is the API surface for the practices bounded context.
 * It handles bad practice detection, resolution, and feedback workflows.
 *
 * <p>All endpoints require workspace membership via {@link WorkspaceScopedController}.
 */
@WorkspaceScopedController
@RequestMapping("/practices")
@RequiredArgsConstructor
@Tag(name = "Code Practices", description = "Bad practice detection and resolution")
public class PracticesController {

    private final PullRequestBadPracticeDetector detector;
    private final BadPracticeFeedbackService feedbackService;
    private final PullRequestBadPracticeRepository badPracticeRepository;
    private final BadPracticeDetectionRepository detectionRepository;
    private final UserRepository userRepository;
    private final PullRequestRepository pullRequestRepository;
    private final WorkspaceContextResolver workspaceResolver;

    // ══════════════════════════════════════════════════════════════════════════
    // GET endpoints - List and retrieve bad practices
    // ══════════════════════════════════════════════════════════════════════════

    @GetMapping("/user/{login}")
    @Operation(
        summary = "Get bad practices for a user",
        description = "Retrieves all detected bad practices for pull requests assigned to the user"
    )
    @ApiResponse(
        responseCode = "200",
        description = "Bad practices returned",
        content = @Content(schema = @Schema(implementation = UserPracticesDTO.class))
    )
    public ResponseEntity<UserPracticesDTO> getBadPracticesForUser(
        WorkspaceContext workspaceContext,
        @PathVariable String login
    ) {
        Workspace workspace = workspaceResolver.requireWorkspace(workspaceContext);

        List<PullRequest> pullRequests = pullRequestRepository.findAssignedByLoginAndStates(
            login,
            Set.of(Issue.State.OPEN),
            workspace.getId()
        );

        List<PullRequestWithBadPracticesDTO> prWithBadPractices = pullRequests
            .stream()
            .map(pr -> {
                BadPracticeDetection lastDetection = detectionRepository.findMostRecentByPullRequestId(pr.getId());

                List<PullRequestBadPracticeDTO> badPractices = lastDetection == null
                    ? List.of()
                    : lastDetection
                          .getBadPractices()
                          .stream()
                          .map(PullRequestBadPracticeDTO::fromPullRequestBadPractice)
                          .toList();

                String summary = lastDetection != null ? lastDetection.getSummary() : "";
                return new PullRequestWithBadPracticesDTO(
                    pr.getId(),
                    pr.getNumber(),
                    pr.getTitle(),
                    pr.getHtmlUrl(),
                    summary,
                    badPractices
                );
            })
            .collect(Collectors.toList());

        return ResponseEntity.ok(new UserPracticesDTO(login, prWithBadPractices));
    }

    @GetMapping("/pullrequest/{pullRequestId}")
    @Operation(
        summary = "Get bad practices for a pull request",
        description = "Retrieves all detected bad practices for a specific pull request"
    )
    @ApiResponse(
        responseCode = "200",
        description = "Bad practices returned",
        content = @Content(schema = @Schema(implementation = PullRequestWithBadPracticesDTO.class))
    )
    public ResponseEntity<PullRequestWithBadPracticesDTO> getBadPracticesForPullRequest(
        WorkspaceContext workspaceContext,
        @PathVariable Long pullRequestId
    ) {
        Workspace workspace = workspaceResolver.requireWorkspace(workspaceContext);
        PullRequest pr = requirePullRequestInWorkspace(pullRequestId, workspace);

        BadPracticeDetection lastDetection = detectionRepository.findMostRecentByPullRequestId(pr.getId());

        List<PullRequestBadPracticeDTO> badPractices = lastDetection == null
            ? List.of()
            : lastDetection
                  .getBadPractices()
                  .stream()
                  .map(PullRequestBadPracticeDTO::fromPullRequestBadPractice)
                  .toList();

        String summary = lastDetection != null ? lastDetection.getSummary() : "";
        return ResponseEntity.ok(
            new PullRequestWithBadPracticesDTO(
                pr.getId(),
                pr.getNumber(),
                pr.getTitle(),
                pr.getHtmlUrl(),
                summary,
                badPractices
            )
        );
    }

    @GetMapping("/badpractice/{id}")
    @Operation(
        summary = "Get a specific bad practice",
        description = "Retrieves details of a specific bad practice by ID"
    )
    @ApiResponse(
        responseCode = "200",
        description = "Bad practice returned",
        content = @Content(schema = @Schema(implementation = PullRequestBadPracticeDTO.class))
    )
    public ResponseEntity<PullRequestBadPracticeDTO> getBadPractice(
        WorkspaceContext workspaceContext,
        @PathVariable Long id
    ) {
        Workspace workspace = workspaceResolver.requireWorkspace(workspaceContext);
        PullRequestBadPractice badPractice = requireBadPracticeInWorkspace(id, workspace);
        return ResponseEntity.ok(PullRequestBadPracticeDTO.fromPullRequestBadPractice(badPractice));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // POST endpoints - Detection, resolution, and feedback
    // ══════════════════════════════════════════════════════════════════════════

    @PostMapping("/user/{login}/detect")
    @Operation(
        summary = "Detect bad practices for a user",
        description = "Triggers bad practice detection for all pull requests of the specified user"
    )
    @ApiResponse(responseCode = "200", description = "Detection completed successfully")
    @ApiResponse(responseCode = "400", description = "Detection failed due to no updates on pull requests")
    public ResponseEntity<DetectionResultDTO> detectForUser(
        WorkspaceContext workspaceContext,
        @PathVariable String login
    ) {
        Workspace workspace = workspaceResolver.requireWorkspace(workspaceContext);
        User currentUser = requireCurrentUser();
        requireSameUser(currentUser, login);

        DetectionResult result = detector.detectForUser(workspace.getId(), login);
        return ResponseEntity.ok(new DetectionResultDTO(result));
    }

    @PostMapping("/pullrequest/{pullRequestId}/detect")
    @Operation(
        summary = "Detect bad practices for a pull request",
        description = "Triggers bad practice detection for a specific pull request"
    )
    @ApiResponse(responseCode = "200", description = "Detection completed successfully")
    @ApiResponse(responseCode = "400", description = "Detection failed due to no updates on pull request")
    public ResponseEntity<DetectionResultDTO> detectForPullRequest(
        WorkspaceContext workspaceContext,
        @PathVariable Long pullRequestId
    ) {
        Workspace workspace = workspaceResolver.requireWorkspace(workspaceContext);
        User currentUser = requireCurrentUser();
        PullRequest pullRequest = requirePullRequestInWorkspace(pullRequestId, workspace);
        requireAssignee(pullRequest, currentUser);

        DetectionResult result = detector.detectAndSyncBadPractices(pullRequest);
        return ResponseEntity.ok(new DetectionResultDTO(result));
    }

    @PostMapping("/badpractice/{id}/resolve")
    @Operation(
        summary = "Resolve a bad practice",
        description = "Updates the state of a bad practice to FIXED, WONT_FIX, or WRONG"
    )
    @ApiResponse(responseCode = "200", description = "Bad practice resolved successfully")
    public ResponseEntity<Void> resolve(
        WorkspaceContext workspaceContext,
        @PathVariable Long id,
        @RequestParam("state") PullRequestBadPracticeState state
    ) {
        Workspace workspace = workspaceResolver.requireWorkspace(workspaceContext);
        User currentUser = requireCurrentUser();
        PullRequestBadPractice badPractice = requireBadPracticeInWorkspace(id, workspace);
        requireAssignee(badPractice.getPullrequest(), currentUser);
        requireValidResolveState(state);

        badPractice.setState(state);
        badPracticeRepository.save(badPractice);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/badpractice/{id}/feedback")
    @Operation(
        summary = "Provide feedback for a bad practice",
        description = "Submits user feedback for a detected bad practice"
    )
    @ApiResponse(responseCode = "200", description = "Feedback submitted successfully")
    @ApiResponse(responseCode = "403", description = "User is not an assignee of the pull request")
    public ResponseEntity<Void> provideFeedback(
        WorkspaceContext workspaceContext,
        @PathVariable Long id,
        @RequestBody @Valid BadPracticeFeedbackDTO feedback
    ) {
        Workspace workspace = workspaceResolver.requireWorkspace(workspaceContext);
        User currentUser = requireCurrentUser();
        PullRequestBadPractice badPractice = requireBadPracticeInWorkspace(id, workspace);
        requireAssignee(badPractice.getPullrequest(), currentUser);

        feedbackService.provideFeedback(workspace, badPractice, feedback);
        return ResponseEntity.ok().build();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Helper methods
    // ══════════════════════════════════════════════════════════════════════════

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
        PullRequest pr = pullRequestRepository
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
        if (!belongsToWorkspace(bp.getPullrequest(), workspace)) {
            throw new EntityNotFoundException("BadPractice", badPracticeId);
        }
        return bp;
    }

    private boolean belongsToWorkspace(PullRequest pullRequest, Workspace workspace) {
        return (
            pullRequest != null &&
            pullRequest.getRepository() != null &&
            pullRequest.getRepository().getOrganization() != null &&
            pullRequest.getRepository().getOrganization().getWorkspaceId() != null &&
            pullRequest.getRepository().getOrganization().getWorkspaceId().equals(workspace.getId())
        );
    }

    // ══════════════════════════════════════════════════════════════════════════
    // DTOs for API responses
    // ══════════════════════════════════════════════════════════════════════════

    /** Response for detection operations */
    public record DetectionResultDTO(DetectionResult result) {}

    /** Response for user bad practices listing */
    public record UserPracticesDTO(String login, List<PullRequestWithBadPracticesDTO> pullRequests) {}

    /** Response for pull request with its bad practices */
    public record PullRequestWithBadPracticesDTO(
        Long id,
        int number,
        String title,
        String htmlUrl,
        String summary,
        List<PullRequestBadPracticeDTO> badPractices
    ) {}
}
