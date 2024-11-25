package de.tum.in.www1.hephaestus.activitydashboard;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
}
