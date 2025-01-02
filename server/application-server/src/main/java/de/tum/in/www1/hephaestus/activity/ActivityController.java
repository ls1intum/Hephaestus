package de.tum.in.www1.hephaestus.activity;

import de.tum.in.www1.hephaestus.activity.model.ActivityDTO;
import de.tum.in.www1.hephaestus.activity.model.PullRequestBadPracticeDTO;
import java.util.List;

import de.tum.in.www1.hephaestus.activity.model.PullRequestBadPracticeRuleDTO;
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
        List<PullRequestBadPracticeDTO> badPractices = activityService.detectBadPractices(login);
        return ResponseEntity.ok(badPractices);
    }

    @GetMapping("/rules/{repository")
    public ResponseEntity<List<PullRequestBadPracticeRuleDTO>> getRulesByRepository(@PathVariable String repository) {
        List<PullRequestBadPracticeRuleDTO> rules = activityService.getRules(repository);
        return ResponseEntity.ok(rules);
    }

    @PostMapping("/rules")
    public ResponseEntity<PullRequestBadPracticeRuleDTO> updateOrCreateRule(@RequestBody PullRequestBadPracticeRuleDTO rule) {
        PullRequestBadPracticeRuleDTO createdRule = activityService.createOrUpdateRule(rule);
        return ResponseEntity.ok(createdRule);
    }
}
