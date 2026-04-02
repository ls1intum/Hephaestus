package de.tum.in.www1.hephaestus.agent.job;

import de.tum.in.www1.hephaestus.agent.AgentJobType;
import de.tum.in.www1.hephaestus.agent.handler.PullRequestReviewSubmissionRequest;
import de.tum.in.www1.hephaestus.gitprovider.common.events.EventPayload;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequest;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequestRepository;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.WriteOperation;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

/**
 * Dev-only actuator endpoint for manually triggering PR reviews without NATS.
 * Enabled by setting hephaestus.dev.trigger-enabled=true.
 *
 * Usage: POST /actuator/dev-trigger-review (with prId and workspaceId params)
 */
@Component
@Endpoint(id = "dev-trigger-review")
@ConditionalOnProperty(name = "hephaestus.dev.trigger-enabled", havingValue = "true")
public class DevTriggerController {

    private static final Logger log = LoggerFactory.getLogger(DevTriggerController.class);

    private final AgentJobService agentJobService;
    private final PullRequestRepository pullRequestRepository;

    public DevTriggerController(AgentJobService agentJobService, PullRequestRepository pullRequestRepository) {
        this.agentJobService = agentJobService;
        this.pullRequestRepository = pullRequestRepository;
    }

    @WriteOperation
    public String triggerReview(@Nullable Long prId, @Nullable Long workspaceId) {
        if (prId == null || workspaceId == null) {
            return "Error: prId and workspaceId are required";
        }

        PullRequest pr = pullRequestRepository.findById(prId).orElse(null);
        if (pr == null) {
            return "PR not found: " + prId;
        }

        if (pr.getHeadRefOid() == null || pr.getHeadRefName() == null || pr.getBaseRefName() == null) {
            return (
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
            return "Job submitted: " + job.get().getId();
        } else {
            return "No job created (no enabled agent config?)";
        }
    }
}
