package de.tum.in.www1.hephaestus.agent.job;

import static de.tum.in.www1.hephaestus.gitprovider.common.events.DomainEvent.TriggerEventNames;

import de.tum.in.www1.hephaestus.agent.AgentJobType;
import de.tum.in.www1.hephaestus.agent.handler.PullRequestReviewSubmissionRequest;
import de.tum.in.www1.hephaestus.gitprovider.common.events.DomainEvent;
import de.tum.in.www1.hephaestus.gitprovider.common.events.EventContext;
import de.tum.in.www1.hephaestus.gitprovider.common.events.EventPayload;
import de.tum.in.www1.hephaestus.gitprovider.issue.Issue;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequest;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequestRepository;
import de.tum.in.www1.hephaestus.practices.review.GateDecision;
import de.tum.in.www1.hephaestus.practices.review.PracticeReviewDetectionGate;
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
 * Listens for PR and review domain events and submits practice-aware agent jobs.
 *
 * <p>Uses {@code @Async @TransactionalEventListener(AFTER_COMMIT)} to avoid blocking the webhook
 * processing thread and to ensure entities are committed before we read them.
 *
 * <p>Each handler follows a layered filtering strategy:
 * <ol>
 *   <li><b>Listener pre-checks</b> (data validity): sync events, closed/merged state, entity
 *       existence, branch info — cheap checks that reject invalid events before any DB work.</li>
 *   <li><b>Detection gate</b> (business policy): labels, drafts, workspace resolution, agent
 *       config, practice matching by trigger events, user roles — the full 9-step gate that
 *       decides whether practices warrant an agent review.</li>
 * </ol>
 *
 * <p><b>Transaction design:</b> Each handler runs in a {@code REQUIRES_NEW} transaction.
 * The gate's repository calls join this transaction, so the Keycloak HTTP call (gate steps 8–9)
 * holds the DB connection. This is acceptable because: the circuit breaker bounds Keycloak
 * latency, {@code @Async} prevents blocking the webhook thread, and {@code runForAllUsers=true}
 * skips Keycloak entirely. See {@link PracticeReviewDetectionGate} for the standalone contract.
 *
 * <p>Coexists with {@code BadPracticeEventListener} — they are independent systems listening
 * to the same events. Only active when the NATS submitter is available.
 */
@Component
@ConditionalOnProperty(prefix = "hephaestus.agent.nats", name = "enabled", havingValue = "true")
public class AgentJobEventListener {

    private static final Logger log = LoggerFactory.getLogger(AgentJobEventListener.class);

    private final AgentJobService agentJobService;
    private final PullRequestRepository pullRequestRepository;
    private final PracticeReviewDetectionGate practiceReviewDetectionGate;

    public AgentJobEventListener(
        AgentJobService agentJobService,
        PullRequestRepository pullRequestRepository,
        PracticeReviewDetectionGate practiceReviewDetectionGate
    ) {
        this.agentJobService = agentJobService;
        this.pullRequestRepository = pullRequestRepository;
        this.practiceReviewDetectionGate = practiceReviewDetectionGate;
    }

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPullRequestCreated(DomainEvent.PullRequestCreated event) {
        handlePullRequestEvent(event.pullRequest(), event.context(), TriggerEventNames.PULL_REQUEST_CREATED);
    }

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPullRequestReady(DomainEvent.PullRequestReady event) {
        handlePullRequestEvent(event.pullRequest(), event.context(), TriggerEventNames.PULL_REQUEST_READY);
    }

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPullRequestSynchronized(DomainEvent.PullRequestSynchronized event) {
        handlePullRequestEvent(event.pullRequest(), event.context(), TriggerEventNames.PULL_REQUEST_SYNCHRONIZED);
    }

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onReviewSubmitted(DomainEvent.ReviewSubmitted event) {
        handleReviewEvent(event.review(), event.context());
    }

    // ── PR event handling ───────────────────────────────────────────────────

    private void handlePullRequestEvent(
        EventPayload.PullRequestData prData,
        EventContext context,
        String triggerEventName
    ) {
        // 1. Skip sync events — agent reviews are for real-time activity only
        if (context.isSync()) {
            return;
        }

        // 2. Skip closed/merged PRs — event validity, not the gate's concern
        if (prData.state() == Issue.State.CLOSED || prData.state() == Issue.State.MERGED || prData.isMerged()) {
            return;
        }

        try {
            // 3. Fetch entity with all associations needed by the gate
            PullRequest pr = pullRequestRepository.findByIdWithAllForGate(prData.id()).orElse(null);
            if (pr == null) {
                log.warn("Cannot submit agent job: PR not found, prId={}", prData.id());
                return;
            }

            // 4. Validate branch info (headRefOid is null for GitLab webhooks)
            if (!hasBranchInfo(pr, prData.id())) {
                return;
            }

            // 5. Evaluate practice detection gate (9-step business policy)
            switch (practiceReviewDetectionGate.evaluate(pr, triggerEventName)) {
                case GateDecision.Skip skip -> log.debug(
                    "Agent job skipped by practice gate: prNumber={}, repoName={}, event={}, reason={}",
                    prData.number(),
                    prData.repository().nameWithOwner(),
                    triggerEventName,
                    skip.reason()
                );
                case GateDecision.Detect detect -> submitJob(prData, pr, detect, triggerEventName);
            }
        } catch (Exception e) {
            log.error(
                "Failed to process PR event: prNumber={}, repoName={}, event={}",
                prData.number(),
                prData.repository().nameWithOwner(),
                triggerEventName,
                e
            );
        }
    }

    // ── Review event handling ───────────────────────────────────────────────

    private void handleReviewEvent(EventPayload.ReviewData reviewData, EventContext context) {
        // 1. Skip sync events
        if (context.isSync()) {
            return;
        }

        try {
            // 2. Fetch PR entity (ReviewSubmitted carries ReviewData, not PullRequestData)
            PullRequest pr = pullRequestRepository.findByIdWithAllForGate(reviewData.pullRequestId()).orElse(null);
            if (pr == null) {
                log.warn(
                    "Cannot submit agent job for review: PR not found, reviewId={}, pullRequestId={}",
                    reviewData.id(),
                    reviewData.pullRequestId()
                );
                return;
            }

            // 3. Skip closed/merged — reviews can arrive on merged PRs (drive-by reviews)
            if (pr.getState() == Issue.State.CLOSED || pr.getState() == Issue.State.MERGED || pr.isMerged()) {
                return;
            }

            // 4. Validate branch info
            if (!hasBranchInfo(pr, pr.getId())) {
                return;
            }

            // 5. Evaluate practice detection gate
            switch (practiceReviewDetectionGate.evaluate(pr, TriggerEventNames.REVIEW_SUBMITTED)) {
                case GateDecision.Skip skip -> log.debug(
                    "Agent job skipped by practice gate for review: reviewId={}, prId={}, reason={}",
                    reviewData.id(),
                    pr.getId(),
                    skip.reason()
                );
                case GateDecision.Detect detect -> {
                    // 6. Construct PullRequestData from entity (ReviewSubmitted does not carry it)
                    EventPayload.PullRequestData prData = EventPayload.PullRequestData.from(pr);
                    submitJob(prData, pr, detect, TriggerEventNames.REVIEW_SUBMITTED);
                }
            }
        } catch (Exception e) {
            log.error(
                "Failed to process review event: reviewId={}, pullRequestId={}",
                reviewData.id(),
                reviewData.pullRequestId(),
                e
            );
        }
    }

    // ── Shared helpers ──────────────────────────────────────────────────────

    private boolean hasBranchInfo(PullRequest pr, Long prId) {
        if (pr.getHeadRefOid() == null || pr.getHeadRefName() == null || pr.getBaseRefName() == null) {
            log.warn(
                "Cannot submit agent job: missing branch info, prId={}, headRefOid={}, headRefName={}, baseRefName={}",
                prId,
                pr.getHeadRefOid(),
                pr.getHeadRefName(),
                pr.getBaseRefName()
            );
            return false;
        }
        return true;
    }

    private void submitJob(
        EventPayload.PullRequestData prData,
        PullRequest pr,
        GateDecision.Detect detect,
        String triggerEventName
    ) {
        PullRequestReviewSubmissionRequest request = new PullRequestReviewSubmissionRequest(
            prData,
            pr.getHeadRefName(),
            pr.getHeadRefOid(),
            pr.getBaseRefName()
        );

        try {
            agentJobService
                .submit(detect.workspace().getId(), AgentJobType.PULL_REQUEST_REVIEW, request)
                .ifPresent(job ->
                    log.info(
                        "Agent job submitted: jobId={}, prNumber={}, repoName={}, event={}, matchedPractices={}",
                        job.getId(),
                        prData.number(),
                        prData.repository().nameWithOwner(),
                        triggerEventName,
                        detect.matchedPractices().size()
                    )
                );
        } catch (Exception e) {
            log.error(
                "Failed to submit agent job: prNumber={}, repoName={}, event={}",
                prData.number(),
                prData.repository().nameWithOwner(),
                triggerEventName,
                e
            );
        }
    }
}
