package de.tum.in.www1.hephaestus.activity;

import de.tum.in.www1.hephaestus.activity.model.ActivityDTO;
import de.tum.in.www1.hephaestus.activity.model.PullRequestBadPracticeDTO;
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

    @PostMapping("/{login}/badpractices")
    public ResponseEntity<List<PullRequestBadPracticeDTO>> detectBadPracticesByUser(@PathVariable String login) {
        List<PullRequestBadPracticeDTO> badPractices = activityService.detectBadPracticesForUser(login);
        return ResponseEntity.ok(badPractices);
    }

    @PostMapping("{prId}/badpractices/")
    public ResponseEntity<List<PullRequestBadPracticeDTO>> detectBadPracticesForPullRequest(@PathVariable Long pullRequestId) {
        List<PullRequestBadPracticeDTO> badPractice = activityService.detectBadPracticesForPullRequest(pullRequestId);
        return ResponseEntity.ok(badPractice);
    }
}
