package de.tum.in.www1.hephaestus.agent.job;

import de.tum.in.www1.hephaestus.agent.AgentJobType;
import de.tum.in.www1.hephaestus.agent.handler.PullRequestReviewSubmissionRequest;
import de.tum.in.www1.hephaestus.gitprovider.common.events.DomainEvent;
import de.tum.in.www1.hephaestus.gitprovider.common.events.EventContext;
import de.tum.in.www1.hephaestus.gitprovider.common.events.EventPayload;
import de.tum.in.www1.hephaestus.gitprovider.issue.Issue;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequest;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequestRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Listens for PR domain events and submits agent jobs for code review.
 *
 * <p>Mirrors the pattern of {@code BadPracticeEventListener}: uses
 * {@code @Async @TransactionalEventListener(AFTER_COMMIT)} to avoid blocking the webhook
 * processing thread and to ensure the PR entity is committed before we read it.
 *
 * <p>Coexists with {@code BadPracticeEventListener} — they are independent systems listening
 * to the same events. Only active when the NATS submitter is available.
 */
@Component
@ConditionalOnBean(AgentJobSubmitter.class)
public class AgentJobEventListener {

    private static final Logger log = LoggerFactory.getLogger(AgentJobEventListener.class);

    private final AgentJobService agentJobService;
    private final PullRequestRepository pullRequestRepository;

    public AgentJobEventListener(AgentJobService agentJobService, PullRequestRepository pullRequestRepository) {
        this.agentJobService = agentJobService;
        this.pullRequestRepository = pullRequestRepository;
    }

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPullRequestCreated(DomainEvent.PullRequestCreated event) {
        handlePullRequestEvent(event.pullRequest(), event.context(), "created");
    }

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPullRequestReady(DomainEvent.PullRequestReady event) {
        handlePullRequestEvent(event.pullRequest(), event.context(), "ready");
    }

    private void handlePullRequestEvent(EventPayload.PullRequestData prData, EventContext context, String eventType) {
        // Skip sync events — agent reviews are for real-time activity only
        if (context.isSync()) {
            return;
        }

        // Skip drafts — not ready for review
        if (prData.isDraft()) {
            log.debug(
                "Skipping agent job for draft PR: prNumber={}, repo={}",
                prData.number(),
                prData.repository().nameWithOwner()
            );
            return;
        }

        // Skip closed/merged PRs
        if (prData.state() == Issue.State.CLOSED || prData.state() == Issue.State.MERGED || prData.isMerged()) {
            return;
        }

        // scopeId is the workspace ID — nullable for some edge cases
        Long workspaceId = context.scopeId();
        if (workspaceId == null) {
            log.warn("Cannot submit agent job: scopeId is null for PR #{}", prData.number());
            return;
        }

        // Fetch entity for branch info not available on EventPayload
        PullRequest pr = pullRequestRepository.findByIdWithRepository(prData.id()).orElse(null);
        if (pr == null) {
            log.warn("Cannot submit agent job: PR not found, prId={}", prData.id());
            return;
        }

        // headRefOid is null for GitLab webhooks
        if (pr.getHeadRefOid() == null || pr.getHeadRefName() == null || pr.getBaseRefName() == null) {
            log.warn(
                "Cannot submit agent job: missing branch info, prId={}, headRefOid={}, headRefName={}, baseRefName={}",
                prData.id(),
                pr.getHeadRefOid(),
                pr.getHeadRefName(),
                pr.getBaseRefName()
            );
            return;
        }

        PullRequestReviewSubmissionRequest request = new PullRequestReviewSubmissionRequest(
            prData,
            pr.getHeadRefName(),
            pr.getHeadRefOid(),
            pr.getBaseRefName()
        );

        try {
            agentJobService
                .submit(workspaceId, AgentJobType.PULL_REQUEST_REVIEW, request)
                .ifPresent(job ->
                    log.info(
                        "Agent job submitted for PR: jobId={}, prNumber={}, repo={}, event={}",
                        job.getId(),
                        prData.number(),
                        prData.repository().nameWithOwner(),
                        eventType
                    )
                );
        } catch (Exception e) {
            log.error(
                "Failed to submit agent job: prNumber={}, repo={}, error={}",
                prData.number(),
                prData.repository().nameWithOwner(),
                e.getMessage(),
                e
            );
        }
    }
}
