package de.tum.in.www1.hephaestus.agent.job;

import de.tum.in.www1.hephaestus.agent.AgentJobType;
import de.tum.in.www1.hephaestus.agent.handler.PullRequestReviewSubmissionRequest;
import de.tum.in.www1.hephaestus.gitprovider.common.events.EventPayload;
import de.tum.in.www1.hephaestus.gitprovider.common.events.RepositoryRef;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequest;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequestRepository;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Dev-only controller for manually triggering PR reviews without NATS.
 * Enabled by setting hephaestus.dev.trigger-enabled=true.
 */
@RestController
@RequestMapping("/actuator/dev")
@ConditionalOnProperty(name = "hephaestus.dev.trigger-enabled", havingValue = "true")
public class DevTriggerController {

    private static final Logger log = LoggerFactory.getLogger(DevTriggerController.class);

    private final AgentJobService agentJobService;
    private final PullRequestRepository pullRequestRepository;

    public DevTriggerController(AgentJobService agentJobService, PullRequestRepository pullRequestRepository) {
        this.agentJobService = agentJobService;
        this.pullRequestRepository = pullRequestRepository;
    }

    @PostMapping("/trigger-review")
    @Transactional
    public ResponseEntity<String> triggerReview(@RequestParam Long prId, @RequestParam Long workspaceId) {
        PullRequest pr = pullRequestRepository.findById(prId).orElse(null);
        if (pr == null) {
            return ResponseEntity.badRequest().body("PR not found: " + prId);
        }

        if (pr.getHeadRefOid() == null || pr.getHeadRefName() == null || pr.getBaseRefName() == null) {
            return ResponseEntity.badRequest().body(
                "PR missing branch info: headRefOid=" +
                    pr.getHeadRefOid() +
                    ", headRefName=" +
                    pr.getHeadRefName() +
                    ", baseRefName=" +
                    pr.getBaseRefName()
            );
        }

        EventPayload.PullRequestData prData = EventPayload.PullRequestData.from(pr);
        PullRequestReviewSubmissionRequest request = new PullRequestReviewSubmissionRequest(
            prData,
            pr.getHeadRefName(),
            pr.getHeadRefOid(),
            pr.getBaseRefName()
        );

        log.info("Dev trigger: submitting review for PR {} ({})", prId, pr.getHtmlUrl());

        Optional<AgentJob> job = agentJobService.submit(workspaceId, AgentJobType.PULL_REQUEST_REVIEW, request);

        if (job.isPresent()) {
            return ResponseEntity.ok("Job submitted: " + job.get().getId());
        } else {
            return ResponseEntity.ok("No job created (no enabled agent config?)");
        }
    }
}
