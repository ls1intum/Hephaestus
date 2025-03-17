package de.tum.in.www1.hephaestus.activity;

import de.tum.in.www1.hephaestus.activity.model.ActivityDTO;
import de.tum.in.www1.hephaestus.activity.model.BadPracticeFeedbackDTO;
import de.tum.in.www1.hephaestus.activity.model.PullRequestBadPracticeDTO;
import jakarta.ws.rs.NotFoundException;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/activity")
public class ActivityController {

    @Autowired
    private ActivityService activityService;

    @GetMapping("/{login}")
    public ResponseEntity<ActivityDTO> getActivityByUser(@PathVariable String login) {
        ActivityDTO activity = activityService.getActivity(login);
        return ResponseEntity.ok(activity);
    }

    @PostMapping("/user/{login}/badpractices")
    public ResponseEntity<List<PullRequestBadPracticeDTO>> detectBadPracticesByUser(@PathVariable String login) {
        List<PullRequestBadPracticeDTO> badPractices = activityService.detectBadPracticesForUser(login);
        return ResponseEntity.ok(badPractices);
    }

    @PostMapping("/pullrequest/{pullRequestId}/badpractices")
    public ResponseEntity<List<PullRequestBadPracticeDTO>> detectBadPracticesForPullRequest(
        @PathVariable Long pullRequestId
    ) {
        List<PullRequestBadPracticeDTO> badPractice = activityService.detectBadPracticesForPullRequest(pullRequestId);
        return ResponseEntity.ok(badPractice);
    }

    @PostMapping("/badpractice/{badPracticeId}/resolve")
    public ResponseEntity<Void> resolveBadPractice(@PathVariable Long badPracticeId) {
        try {
            activityService.resolveBadPractice(badPracticeId);
        } catch (NotFoundException e) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok().build();
    }

    @PostMapping("/badpractice/{badPracticeId}/feedback")
    public ResponseEntity<Void> provideFeedbackForBadPractice(
        @PathVariable Long badPracticeId,
        @RequestBody BadPracticeFeedbackDTO feedback
    ) {
        try {
            activityService.provideFeedbackForBadPractice(badPracticeId, feedback);
        } catch (NotFoundException e) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok().build();
    }
}
