package de.tum.cit.aet.hephaestus.agent.job;

import static de.tum.cit.aet.hephaestus.integration.core.events.ScmDomainEvent.TriggerEventNames;

import de.tum.cit.aet.hephaestus.agent.AgentJobType;
import de.tum.cit.aet.hephaestus.agent.handler.IssueReviewSubmissionRequest;
import de.tum.cit.aet.hephaestus.integration.core.events.EventContext;
import de.tum.cit.aet.hephaestus.integration.core.events.ScmDomainEvent;
import de.tum.cit.aet.hephaestus.integration.core.events.ScmEventPayload;
import de.tum.cit.aet.hephaestus.integration.core.signal.DiscoveredVia;
import de.tum.cit.aet.hephaestus.integration.core.signal.SignalKey;
import de.tum.cit.aet.hephaestus.integration.core.signal.SignalName;
import de.tum.cit.aet.hephaestus.integration.core.signal.SignalRecorder;
import de.tum.cit.aet.hephaestus.integration.core.signal.SignalStateReason;
import de.tum.cit.aet.hephaestus.integration.scm.domain.issue.Issue;
import de.tum.cit.aet.hephaestus.integration.scm.domain.issue.IssueRepository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.signal.ScmSignals;
import de.tum.cit.aet.hephaestus.practices.review.GateDecision;
import de.tum.cit.aet.hephaestus.practices.review.PracticeReviewDetectionGate;
import de.tum.cit.aet.hephaestus.practices.review.TriggerMode;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import de.tum.cit.aet.hephaestus.workspace.WorkspaceResolver;
import java.util.Collections;
import java.util.Set;
import org.jspecify.annotations.Nullable;
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
 * {@code @TransactionalEventListener(AFTER_COMMIT)}, {@code REQUIRES_NEW}).
 */
@Component
@ConditionalOnProperty(prefix = "hephaestus.agent", name = "enabled", havingValue = "true")
public class IssueAgentJobEventListener {

    private static final Logger log = LoggerFactory.getLogger(IssueAgentJobEventListener.class);

    /**
     * The issue fields the review evidence is built from ({@code IssueContentSource}). An update that
     * moves nothing else — a lock, a comment count, a due date, a weight — occasions no review: the
     * mirror still records it, but every practice bound to the occasion would read byte-identical
     * evidence and reach the conclusion it already published.
     */
    private static final Set<String> REVIEWABLE_ISSUE_FIELDS = Set.of(
            ScmDomainEvent.IssueUpdated.TITLE,
            ScmDomainEvent.IssueUpdated.BODY,
            ScmDomainEvent.IssueUpdated.STATE,
            ScmDomainEvent.IssueUpdated.STATE_REASON,
            ScmDomainEvent.IssueUpdated.ISSUE_TYPE,
            ScmDomainEvent.IssueUpdated.MILESTONE,
            ScmDomainEvent.IssueUpdated.RELATIONSHIPS);

    private final AgentJobService agentJobService;
    private final IssueRepository issueRepository;
    private final PracticeReviewDetectionGate practiceReviewDetectionGate;
    private final WorkspaceResolver workspaceResolver;
    private final SignalRecorder signalRecorder;

    public IssueAgentJobEventListener(
            AgentJobService agentJobService,
            IssueRepository issueRepository,
            PracticeReviewDetectionGate practiceReviewDetectionGate,
            WorkspaceResolver workspaceResolver,
            SignalRecorder signalRecorder) {
        this.agentJobService = agentJobService;
        this.issueRepository = issueRepository;
        this.practiceReviewDetectionGate = practiceReviewDetectionGate;
        this.workspaceResolver = workspaceResolver;
        this.signalRecorder = signalRecorder;
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
    public void onIssueUpdated(ScmDomainEvent.IssueUpdated event) {
        if (Collections.disjoint(event.changedFields(), REVIEWABLE_ISSUE_FIELDS)) {
            return;
        }
        handleIssueEvent(event.issue(), event.context(), TriggerEventNames.ISSUE_UPDATED);
    }

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onIssueClosed(ScmDomainEvent.IssueClosed event) {
        handleRetrospectiveIssueEvent(event.issue(), event.context(), TriggerEventNames.ISSUE_CLOSED);
    }

    private void handleIssueEvent(ScmEventPayload.IssueData issueData, EventContext context, String triggerEventName) {
        if (issueData.state() == Issue.State.CLOSED) {
            return;
        }
        dispatchIssueEvent(issueData, context, triggerEventName);
    }

    /**
     * Retrospective counterpart of {@link #handleIssueEvent}, deliberately without its closed-skip: here
     * CLOSED is the trigger's reason to run.
     */
    private void handleRetrospectiveIssueEvent(
            ScmEventPayload.IssueData issueData, EventContext context, String triggerEventName) {
        dispatchIssueEvent(issueData, context, triggerEventName);
    }

    /**
     * Records the observation, then decides whether it warrants a review — the same split as the PR
     * listener, and for the same reason: what we saw is a fact, whether to coach on it is a policy.
     */
    private void dispatchIssueEvent(
            ScmEventPayload.IssueData issueData, EventContext context, String triggerEventName) {
        try {
            SignalKey key = signalKeyFor(issueData, triggerEventName);
            if (key == null) {
                return;
            }

            DiscoveredVia discoveredVia = context.isSync() ? DiscoveredVia.SYNC : DiscoveredVia.EVENT;
            if (!signalRecorder.record(key, context.occurredAt(), discoveredVia)) {
                log.debug(
                        "Signal already settled, not reviewing again: issueId={}, signal={}",
                        issueData.id(),
                        key.signalName());
                return;
            }

            // A history replay would otherwise fire a retrospective review for every issue ever closed;
            // the signal row above still records that we saw it.
            if (context.isSync()) {
                return;
            }

            Issue issue = issueRepository
                    .findByIdWithRepositoryAndAssignees(issueData.id())
                    .orElse(null);
            if (issue == null || issue.getRepository() == null) {
                log.warn(
                        "Cannot submit issue agent job: issue not found or missing repository, issueId={}",
                        issueData.id());
                signalRecorder.markRefused(key, SignalStateReason.ARTIFACT_GONE);
                return;
            }

            switch (practiceReviewDetectionGate.evaluateIssue(issue, key.signalName(), TriggerMode.AUTO)) {
                case GateDecision.Skip skip -> {
                    log.debug(
                            "Issue agent job skipped by practice gate: issueId={}, event={}, reason={}",
                            issue.getId(),
                            triggerEventName,
                            skip.reason());
                    signalRecorder.markRefused(key, skip.resolvedSignalReason());
                }
                case GateDecision.Detect detect -> submitJob(issue, detect, key);
            }
        } catch (Exception e) {
            log.error("Failed to handle issue event: issueId={}, event={}", issueData.id(), triggerEventName, e);
        }
    }

    private @Nullable SignalKey signalKeyFor(ScmEventPayload.IssueData issueData, String triggerEventName) {
        Workspace workspace = workspaceResolver
                .resolveForRepository(issueData.repository().nameWithOwner())
                .orElse(null);
        if (workspace == null) {
            log.debug(
                    "No workspace owns this repository, nothing to record: repoName={}",
                    issueData.repository().nameWithOwner());
            return null;
        }
        SignalName signal = ScmSignals.forTriggerEvent(triggerEventName).orElse(null);
        if (signal == null) {
            log.debug("No signal declared for trigger event, nothing to record: event={}", triggerEventName);
            return null;
        }
        return ScmSignals.issueKey(workspace.getId(), signal, issueData).orElse(null);
    }

    private void submitJob(Issue issue, GateDecision.Detect detect, SignalKey signalKey) {
        IssueReviewSubmissionRequest request = new IssueReviewSubmissionRequest(
                issue.getId(),
                issue.getNumber(),
                issue.requireRepository().getId(),
                issue.requireRepository().getNameWithOwner(),
                issue.getTitle(),
                issue.getBody() != null ? issue.getBody() : "",
                issue.getState() != null ? issue.getState().name() : "OPEN",
                issue.getHtmlUrl(),
                issue.getUpdatedAt(),
                signalKey.signalName());
        agentJobService
                .submit(detect.workspace().getId(), AgentJobType.ISSUE_REVIEW, request, signalKey, detect)
                .ifPresent(job -> log.info(
                        "Submitted issue review job: issueId={}, signal={}, workspaceId={}, jobId={}",
                        issue.getId(),
                        signalKey.signalName(),
                        detect.workspace().getId(),
                        job.getId()));
    }
}
