package de.tum.in.www1.hephaestus.activity;

import de.tum.in.www1.hephaestus.activity.badpractice.PullRequestBadPracticeRepository;
import de.tum.in.www1.hephaestus.activity.model.*;
import de.tum.in.www1.hephaestus.core.exception.AccessForbiddenException;
import de.tum.in.www1.hephaestus.core.exception.EntityNotFoundException;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequest;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequestRepository;
import de.tum.in.www1.hephaestus.gitprovider.user.User;
import de.tum.in.www1.hephaestus.gitprovider.user.UserRepository;
import de.tum.in.www1.hephaestus.workspace.Workspace;
import de.tum.in.www1.hephaestus.workspace.context.WorkspaceContext;
import de.tum.in.www1.hephaestus.workspace.context.WorkspaceContextResolver;
import de.tum.in.www1.hephaestus.workspace.context.WorkspaceScopedController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Controller for activity-related operations within a workspace context.
 * All endpoints require workspace membership via {@link WorkspaceScopedController}.
 */
@WorkspaceScopedController
@RequestMapping("/activity")
@RequiredArgsConstructor
@Tag(name = "Activity", description = "User activity and bad practice detection")
public class ActivityController {

    private final ActivityService activityService;
    private final UserRepository userRepository;
    private final PullRequestRepository pullRequestRepository;
    private final PullRequestBadPracticeRepository pullRequestBadPracticeRepository;
    private final WorkspaceContextResolver workspaceResolver;

    @GetMapping("/{login}")
    @Operation(
        summary = "Get activity for a user",
        description = "Retrieves activity data including pull requests and bad practices for a user"
    )
    @ApiResponse(
        responseCode = "200",
        description = "Activity data returned",
        content = @Content(schema = @Schema(implementation = ActivityDTO.class))
    )
    public ResponseEntity<ActivityDTO> getActivityByUser(
        WorkspaceContext workspaceContext,
        @PathVariable String login
    ) {
        Workspace workspace = workspaceResolver.requireWorkspace(workspaceContext);
        ActivityDTO activity = activityService.getActivity(workspace, login);
        return ResponseEntity.ok(activity);
    }

    @PostMapping("/user/{login}/badpractices")
    @Operation(
        summary = "Detect bad practices for a user",
        description = "Triggers bad practice detection for all pull requests of the specified user"
    )
    @ApiResponse(responseCode = "200", description = "Detection completed successfully")
    @ApiResponse(responseCode = "400", description = "Detection failed due to no updates on pull requests")
    public ResponseEntity<Void> detectBadPracticesByUser(
        WorkspaceContext workspaceContext,
        @PathVariable String login
    ) {
        Workspace workspace = workspaceResolver.requireWorkspace(workspaceContext);
        User currentUser = requireCurrentUser();
        requireSameUser(currentUser, login);

        DetectionResult result = activityService.detectBadPracticesForUser(workspace, login);
        if (result == DetectionResult.ERROR_NO_UPDATE_ON_PULLREQUEST) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok().build();
    }

    @PostMapping("/pullrequest/{pullRequestId}/badpractices")
    @Operation(
        summary = "Detect bad practices for a pull request",
        description = "Triggers bad practice detection for a specific pull request"
    )
    @ApiResponse(responseCode = "200", description = "Detection completed successfully")
    @ApiResponse(responseCode = "400", description = "Detection failed due to no updates on pull request")
    public ResponseEntity<Void> detectBadPracticesForPullRequest(
        WorkspaceContext workspaceContext,
        @PathVariable Long pullRequestId
    ) {
        Workspace workspace = workspaceResolver.requireWorkspace(workspaceContext);
        User currentUser = requireCurrentUser();
        PullRequest pullRequest = requirePullRequestInWorkspace(pullRequestId, workspace);
        requireAssignee(pullRequest, currentUser);

        DetectionResult result = activityService.detectBadPracticesForPullRequest(workspace, pullRequest);
        if (result == DetectionResult.ERROR_NO_UPDATE_ON_PULLREQUEST) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok().build();
    }

    @PostMapping("/badpractice/{badPracticeId}/resolve")
    @Operation(
        summary = "Resolve a bad practice",
        description = "Updates the state of a bad practice to FIXED, WONT_FIX, or WRONG"
    )
    @ApiResponse(responseCode = "200", description = "Bad practice resolved successfully")
    public ResponseEntity<Void> resolveBadPractice(
        WorkspaceContext workspaceContext,
        @PathVariable Long badPracticeId,
        @RequestParam("state") PullRequestBadPracticeState state
    ) {
        Workspace workspace = workspaceResolver.requireWorkspace(workspaceContext);
        User currentUser = requireCurrentUser();
        PullRequestBadPractice badPractice = requireBadPracticeInWorkspace(badPracticeId, workspace);
        requireAssignee(badPractice.getPullrequest(), currentUser);
        requireValidResolveState(state);

        activityService.resolveBadPractice(workspace, badPractice, state);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/badpractice/{badPracticeId}/feedback")
    @Operation(
        summary = "Provide feedback for a bad practice",
        description = "Submits user feedback for a detected bad practice"
    )
    @ApiResponse(responseCode = "200", description = "Feedback submitted successfully")
    public ResponseEntity<Void> provideFeedbackForBadPractice(
        WorkspaceContext workspaceContext,
        @PathVariable Long badPracticeId,
        @RequestBody BadPracticeFeedbackDTO feedback
    ) {
        Workspace workspace = workspaceResolver.requireWorkspace(workspaceContext);
        PullRequestBadPractice badPractice = requireBadPracticeInWorkspace(badPracticeId, workspace);
        activityService.provideFeedbackForBadPractice(workspace, badPractice, feedback);
        return ResponseEntity.ok().build();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Helper methods - throw proper exceptions for consistent RFC-7807 responses
    // ══════════════════════════════════════════════════════════════════════════

    private User requireCurrentUser() {
        return userRepository
            .getCurrentUser()
            .orElseThrow(() -> new AccessForbiddenException("User not authenticated"));
    }

    private void requireSameUser(User currentUser, String login) {
        if (!currentUser.getLogin().equals(login)) {
            throw new AccessForbiddenException("Cannot access activity for another user");
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
        PullRequestBadPractice bp = pullRequestBadPracticeRepository
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
}
