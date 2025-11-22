package de.tum.in.www1.hephaestus.activity;

import de.tum.in.www1.hephaestus.activity.model.*;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequest;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequestRepository;
import de.tum.in.www1.hephaestus.gitprovider.user.UserRepository;
import de.tum.in.www1.hephaestus.workspace.context.WorkspaceContext;
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

    @GetMapping("/{login}")
    public ResponseEntity<ActivityDTO> getActivityByUser(
        WorkspaceContext workspaceContext,
        @PathVariable String login
    ) {
        ActivityDTO activity = activityService.getActivity(workspaceContext, login);
        return ResponseEntity.ok(activity);
    }

    @PostMapping("/user/{login}/badpractices")
    public ResponseEntity<Void> detectBadPracticesByUser(
        WorkspaceContext workspaceContext,
        @PathVariable String login
    ) {
        var user = userRepository.getCurrentUser();

        if (user.isEmpty()) {
            return ResponseEntity.status(401).build();
        } else if (!user.get().getLogin().equals(login)) {
            return ResponseEntity.status(403).build();
        }

        DetectionResult detectionResult = activityService.detectBadPracticesForUser(workspaceContext, login);
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
        var user = userRepository.getCurrentUser();
        PullRequest pullRequest = pullRequestRepository.findById(pullRequestId).orElse(null);

        if (pullRequest == null) {
            return ResponseEntity.notFound().build();
        } else if (user.isEmpty()) {
            return ResponseEntity.status(401).build();
        } else if (!pullRequest.getAssignees().contains(user.get())) {
            return ResponseEntity.status(403).build();
        }

        DetectionResult detectionResult = activityService.detectBadPracticesForPullRequest(
            workspaceContext,
            pullRequest
        );
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
        var user = userRepository.getCurrentUser();
        var badPractice = pullRequestBadPracticeRepository.findById(badPracticeId);

        if (badPractice.isEmpty()) {
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

        activityService.resolveBadPractice(workspaceContext, badPractice.get(), state);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/badpractice/{badPracticeId}/feedback")
    public ResponseEntity<Void> provideFeedbackForBadPractice(
        WorkspaceContext workspaceContext,
        @PathVariable Long badPracticeId,
        @RequestBody BadPracticeFeedbackDTO feedback
    ) {
        Optional<PullRequestBadPractice> badPractice = pullRequestBadPracticeRepository.findById(badPracticeId);

        if (badPractice.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        activityService.provideFeedbackForBadPractice(workspaceContext, badPractice.get(), feedback);
        return ResponseEntity.ok().build();
    }
}
