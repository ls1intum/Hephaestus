package de.tum.in.www1.hephaestus.activity;

import de.tum.in.www1.hephaestus.activity.model.*;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequest;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequestRepository;
import de.tum.in.www1.hephaestus.gitprovider.user.User;
import de.tum.in.www1.hephaestus.gitprovider.user.UserRepository;
import jakarta.ws.rs.NotFoundException;
import java.util.List;

import jakarta.ws.rs.QueryParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/activity")
public class ActivityController {

    @Autowired
    private ActivityService activityService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PullRequestRepository pullRequestRepository;

    @Autowired
    private PullRequestBadPracticeRepository pullRequestBadPracticeRepository;

    @GetMapping("/{login}")
    public ResponseEntity<ActivityDTO> getActivityByUser(@PathVariable String login) {
        ActivityDTO activity = activityService.getActivity(login);
        return ResponseEntity.ok(activity);
    }

    @PostMapping("/user/{login}/badpractices")
    public ResponseEntity<List<PullRequestBadPracticeDTO>> detectBadPracticesByUser(@PathVariable String login) {
        var user = userRepository.getCurrentUser();

        if (user.isEmpty()) {
            return ResponseEntity.notFound().build();
        } else if (!user.get().getLogin().equals(login)) {
            return ResponseEntity.badRequest().build();
        }

        List<PullRequestBadPracticeDTO> badPractices = activityService.detectBadPracticesForUser(login);
        return ResponseEntity.ok(badPractices);
    }

    @PostMapping("/pullrequest/{pullRequestId}/badpractices")
    public ResponseEntity<List<PullRequestBadPracticeDTO>> detectBadPracticesForPullRequest(@PathVariable Long pullRequestId) {
        var user = userRepository.getCurrentUser();
        PullRequest pullRequest = pullRequestRepository.findById(pullRequestId).orElse(null);

        if (pullRequest == null) {
            return ResponseEntity.notFound().build();
        } else if (user.isEmpty()) {
            return ResponseEntity.notFound().build();
        } else if (!pullRequest.getAssignees().contains(user.get())) {
            return ResponseEntity.badRequest().build();
        }

        List<PullRequestBadPracticeDTO> badPractice = activityService.detectBadPracticesForPullRequest(pullRequest);
        return ResponseEntity.ok(badPractice);
    }

    @PostMapping("/badpractice/{badPracticeId}/resolve")
    public ResponseEntity<Void> resolveBadPractice(@PathVariable Long badPracticeId, @QueryParam("state") PullRequestBadPracticeState state) {
        var user = userRepository.getCurrentUser();
        var badPractice = pullRequestBadPracticeRepository.findById(badPracticeId);

        if (badPractice.isEmpty()) {
            return ResponseEntity.notFound().build();
        } else if (user.isEmpty()) {
            return ResponseEntity.notFound().build();
        } else if (!badPractice.get().getPullrequest().getAssignees().contains(user.get())) {
            return ResponseEntity.badRequest().build();
        } else if (state != PullRequestBadPracticeState.FIXED && state != PullRequestBadPracticeState.WONT_FIX) {
            return ResponseEntity.badRequest().build();
        }

        activityService.resolveBadPractice(badPractice.get(), state);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/badpractice/{badPracticeId}/feedback")
    public ResponseEntity<Void> provideFeedbackForBadPractice(
        @PathVariable Long badPracticeId,
        @RequestBody BadPracticeFeedbackDTO feedback
    ) {
        var badPractice = pullRequestBadPracticeRepository.findById(badPracticeId);

        if (badPractice.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        activityService.provideFeedbackForBadPractice(badPractice.get(), feedback);
        return ResponseEntity.ok().build();
    }
}
