package de.tum.in.www1.hephaestus.activity;

import de.tum.in.www1.hephaestus.activity.model.*;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequest;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequestRepository;
import de.tum.in.www1.hephaestus.gitprovider.user.UserRepository;
import de.tum.in.www1.hephaestus.workspace.Workspace;
import de.tum.in.www1.hephaestus.workspace.context.WorkspaceContext;
import de.tum.in.www1.hephaestus.workspace.context.WorkspaceContextResolver;
import de.tum.in.www1.hephaestus.workspace.context.WorkspaceScopedController;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@WorkspaceScopedController
@RequestMapping("/activity")
@RequiredArgsConstructor
public class ActivityController {

    private final ActivityService activityService;
    private final UserRepository userRepository;
    private final PullRequestRepository pullRequestRepository;
    private final PullRequestBadPracticeRepository pullRequestBadPracticeRepository;
    private final WorkspaceContextResolver workspaceResolver;

    @GetMapping("/{login}")
    public ResponseEntity<ActivityDTO> getActivityByUser(
        WorkspaceContext workspaceContext,
        @PathVariable String login
    ) {
        Workspace workspace = workspaceResolver.requireWorkspace(workspaceContext);
        ActivityDTO activity = activityService.getActivity(workspace, login);
        return ResponseEntity.ok(activity);
    }

    @PostMapping("/user/{login}/badpractices")
    public ResponseEntity<Void> detectBadPracticesByUser(
        WorkspaceContext workspaceContext,
        @PathVariable String login
    ) {
        Workspace workspace = workspaceResolver.requireWorkspace(workspaceContext);
        var user = userRepository.getCurrentUser();

        if (user.isEmpty()) {
            return ResponseEntity.status(401).build();
        } else if (!user.get().getLogin().equals(login)) {
            return ResponseEntity.status(403).build();
        }

        DetectionResult detectionResult = activityService.detectBadPracticesForUser(workspace, login);
        if (detectionResult == DetectionResult.ERROR_NO_UPDATE_ON_PULLREQUEST) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok().build();
    }

    @PostMapping("/pullrequest/{pullRequestId}/badpractices")
    public ResponseEntity<Void> detectBadPracticesForPullRequest(
        WorkspaceContext workspaceContext,
        @PathVariable Long pullRequestId
    ) {
        Workspace workspace = workspaceResolver.requireWorkspace(workspaceContext);
        var user = userRepository.getCurrentUser();
        PullRequest pullRequest = pullRequestRepository.findById(pullRequestId).orElse(null);

        if (pullRequest == null) {
            return ResponseEntity.notFound().build();
        } else if (!belongsToWorkspace(pullRequest, workspace)) {
            return ResponseEntity.notFound().build();
        } else if (user.isEmpty()) {
            return ResponseEntity.status(401).build();
        } else if (!pullRequest.getAssignees().contains(user.get())) {
            return ResponseEntity.status(403).build();
        }

        DetectionResult detectionResult = activityService.detectBadPracticesForPullRequest(workspace, pullRequest);
        if (detectionResult == DetectionResult.ERROR_NO_UPDATE_ON_PULLREQUEST) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok().build();
    }

    @PostMapping("/badpractice/{badPracticeId}/resolve")
    public ResponseEntity<Void> resolveBadPractice(
        WorkspaceContext workspaceContext,
        @PathVariable Long badPracticeId,
        @RequestParam("state") PullRequestBadPracticeState state
    ) {
        Workspace workspace = workspaceResolver.requireWorkspace(workspaceContext);
        var user = userRepository.getCurrentUser();
        var badPractice = pullRequestBadPracticeRepository.findById(badPracticeId);

        if (badPractice.isEmpty()) {
            return ResponseEntity.notFound().build();
        } else if (!belongsToWorkspace(badPractice.get().getPullrequest(), workspace)) {
            return ResponseEntity.notFound().build();
        } else if (user.isEmpty()) {
            return ResponseEntity.status(401).build();
        } else if (!badPractice.get().getPullrequest().getAssignees().contains(user.get())) {
            return ResponseEntity.status(403).build();
        } else if (
            state != PullRequestBadPracticeState.FIXED &&
            state != PullRequestBadPracticeState.WONT_FIX &&
            state != PullRequestBadPracticeState.WRONG
        ) {
            return ResponseEntity.badRequest().build();
        }

        activityService.resolveBadPractice(workspace, badPractice.get(), state);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/badpractice/{badPracticeId}/feedback")
    public ResponseEntity<Void> provideFeedbackForBadPractice(
        WorkspaceContext workspaceContext,
        @PathVariable Long badPracticeId,
        @RequestBody BadPracticeFeedbackDTO feedback
    ) {
        Workspace workspace = workspaceResolver.requireWorkspace(workspaceContext);
        Optional<PullRequestBadPractice> badPractice = pullRequestBadPracticeRepository.findById(badPracticeId);

        if (badPractice.isEmpty()) {
            return ResponseEntity.notFound().build();
        } else if (!belongsToWorkspace(badPractice.get().getPullrequest(), workspace)) {
            return ResponseEntity.notFound().build();
        }

        activityService.provideFeedbackForBadPractice(workspace, badPractice.get(), feedback);
        return ResponseEntity.ok().build();
    }

    private boolean belongsToWorkspace(PullRequest pullRequest, Workspace workspace) {
        if (
            pullRequest == null ||
            pullRequest.getRepository() == null ||
            pullRequest.getRepository().getOrganization() == null ||
            pullRequest.getRepository().getOrganization().getWorkspace() == null
        ) {
            return false;
        }
        return pullRequest.getRepository().getOrganization().getWorkspace().getId().equals(workspace.getId());
    }
}
