package de.tum.cit.aet.hephaestus.agent.job;

import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Dev-only REST endpoint for manually triggering PR reviews without NATS.
 * Enabled by setting hephaestus.dev.trigger-enabled=true.
 *
 * Usage: POST /api/dev/trigger-review?prId=...&workspaceId=...
 */
@RestController
@ConditionalOnProperty(name = "hephaestus.dev.trigger-enabled", havingValue = "true")
@PreAuthorize("permitAll()")
@WorkspaceAgnostic("Dev-only endpoint; workspace ID passed as request parameter")
public class DevTriggerController {

    private final AgentJobService agentJobService;

    public DevTriggerController(AgentJobService agentJobService) {
        this.agentJobService = agentJobService;
    }

    @PostMapping("/api/dev/trigger-review")
    public String triggerReview(
        @RequestParam @Nullable Long prId,
        @RequestParam @Nullable Long issueId,
        @RequestParam @Nullable Long workspaceId
    ) {
        if (workspaceId == null || (prId == null && issueId == null)) {
            return "Error: workspaceId and one of prId / issueId are required";
        }
        if (issueId != null) {
            return agentJobService.submitDetectionForIssue(workspaceId, issueId);
        }
        return agentJobService.submitReviewForPullRequest(workspaceId, prId);
    }
}
