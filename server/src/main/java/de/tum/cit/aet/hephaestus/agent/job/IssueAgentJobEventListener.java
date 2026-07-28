package de.tum.cit.aet.hephaestus.agent.job;

import static de.tum.cit.aet.hephaestus.integration.core.events.ScmDomainEvent.TriggerEventNames;

import de.tum.cit.aet.hephaestus.agent.AgentJobType;
import de.tum.cit.aet.hephaestus.agent.handler.IssueReviewSubmissionRequest;
import de.tum.cit.aet.hephaestus.integration.core.events.EventContext;
import de.tum.cit.aet.hephaestus.integration.core.events.ScmDomainEvent;
import de.tum.cit.aet.hephaestus.integration.core.events.ScmEventPayload;
import de.tum.cit.aet.hephaestus.integration.scm.domain.issue.Issue;
import de.tum.cit.aet.hephaestus.integration.scm.domain.issue.IssueRepository;
import de.tum.cit.aet.hephaestus.practices.review.GateDecision;
import de.tum.cit.aet.hephaestus.practices.review.PracticeReviewDetectionGate;
import de.tum.cit.aet.hephaestus.practices.review.TriggerMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Issue-side mirror of {@link AgentJobEventListener}: listens for issue domain events and submits
 * practice-aware {@code ISSUE_REVIEW} jobs through the same gate. Only ISSUE-focused practices carry
 * issue trigger events, so {@link PracticeReviewDetectionGate#evaluateIssue} short-circuits with no
 * cost for PR-only workspaces (no matching practices → skip before any agent-config / role work).
 *
 * <p>Same transaction + async contract as the PR listener ({@code @Async},
 * {@code @TransactionalEventListener(AFTER_COMMIT)}, {@code REQUIRES_NEW}). Only active when the
 * agent job queue is enabled.
 */
@Component
@ConditionalOnProperty(prefix = "hephaestus.agent", name = "enabled", havingValue = "true")
public class IssueAgentJobEventListener {

    private static final Logger log = LoggerFactory.getLogger(IssueAgentJobEventListener.class);

    private final AgentJobService agentJobService;
    private final IssueRepository issueRepository;
    private final PracticeReviewDetectionGate practiceReviewDetectionGate;

    public IssueAgentJobEventListener(
        AgentJobService agentJobService,
        IssueRepository issueRepository,
        PracticeReviewDetectionGate practiceReviewDetectionGate
    ) {
        this.agentJobService = agentJobService;
        this.issueRepository = issueRepository;
        this.practiceReviewDetectionGate = practiceReviewDetectionGate;
    }

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onIssueCreated(ScmDomainEvent.IssueCreated event) {
        handleIssueEvent(event.issue(), event.context(), TriggerEventNames.ISSUE_CREATED);
    }

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onIssueLabeled(ScmDomainEvent.IssueLabeled event) {
        handleIssueEvent(event.issue(), event.context(), TriggerEventNames.ISSUE_LABELED);
    }

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onIssueClosed(ScmDomainEvent.IssueClosed event) {
        handleRetrospectiveIssueEvent(event.issue(), event.context(), TriggerEventNames.ISSUE_CLOSED);
    }

    private void handleIssueEvent(ScmEventPayload.IssueData issueData, EventContext context, String triggerEventName) {
        // Agent reviews are for real-time activity only.
        if (context.isSync()) {
            return;
        }
        if (issueData.state() == Issue.State.CLOSED) {
            return;
        }
        dispatchIssueEvent(issueData, triggerEventName);
    }

    /**
     * Retrospective counterpart of {@link #handleIssueEvent}, deliberately without its closed-skip: here
     * CLOSED is the trigger's reason to run. Sync is still skipped so a history replay does not fire a
     * retrospective review for every issue the repository ever closed.
     */
    private void handleRetrospectiveIssueEvent(
        ScmEventPayload.IssueData issueData,
        EventContext context,
        String triggerEventName
    ) {
        if (context.isSync()) {
            return;
        }
        dispatchIssueEvent(issueData, triggerEventName);
    }

    private void dispatchIssueEvent(ScmEventPayload.IssueData issueData, String triggerEventName) {
        try {
            Issue issue = issueRepository.findByIdWithRepositoryAndAssignees(issueData.id()).orElse(null);
            if (issue == null || issue.getRepository() == null) {
                log.warn(
                    "Cannot submit issue agent job: issue not found or missing repository, issueId={}",
                    issueData.id()
                );
                return;
            }

            switch (practiceReviewDetectionGate.evaluateIssue(issue, triggerEventName, TriggerMode.AUTO)) {
                case GateDecision.Skip skip -> log.debug(
                    "Issue agent job skipped by practice gate: issueId={}, event={}, reason={}",
                    issue.getId(),
                    triggerEventName,
                    skip.reason()
                );
                case GateDecision.Detect detect -> submitJob(issue, detect, triggerEventName);
            }
        } catch (Exception e) {
            log.error("Failed to handle issue event: issueId={}, event={}", issueData.id(), triggerEventName, e);
        }
    }

    private void submitJob(Issue issue, GateDecision.Detect detect, String triggerEventName) {
        IssueReviewSubmissionRequest request = new IssueReviewSubmissionRequest(
            issue.getId(),
            issue.getNumber(),
            issue.getRepository().getId(),
            issue.getRepository().getNameWithOwner(),
            issue.getTitle(),
            issue.getBody() != null ? issue.getBody() : "",
            issue.getState() != null ? issue.getState().name() : "OPEN",
            issue.getUpdatedAt(),
            triggerEventName
        );
        agentJobService
            .submit(detect.workspace().getId(), AgentJobType.ISSUE_REVIEW, request)
            .ifPresent(job ->
                log.info(
                    "Submitted issue review job: issueId={}, event={}, workspaceId={}, jobId={}",
                    issue.getId(),
                    triggerEventName,
                    detect.workspace().getId(),
                    job.getId()
                )
            );
    }
}
