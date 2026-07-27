package de.tum.cit.aet.hephaestus.agent.job;

import static de.tum.cit.aet.hephaestus.integration.core.events.ScmDomainEvent.TriggerEventNames;

import de.tum.cit.aet.hephaestus.agent.AgentJobType;
import de.tum.cit.aet.hephaestus.agent.handler.PullRequestReviewSubmissionRequest;
import de.tum.cit.aet.hephaestus.integration.core.events.EventContext;
import de.tum.cit.aet.hephaestus.integration.core.events.ScmDomainEvent;
import de.tum.cit.aet.hephaestus.integration.core.events.ScmEventPayload;
import de.tum.cit.aet.hephaestus.integration.scm.domain.issue.Issue;
import de.tum.cit.aet.hephaestus.integration.scm.domain.pullrequest.PullRequest;
import de.tum.cit.aet.hephaestus.integration.scm.domain.pullrequest.PullRequestRepository;
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
 * Listens for PR and review domain events and submits practice-aware agent jobs.
 *
 * <p>Uses {@code @Async @TransactionalEventListener(AFTER_COMMIT)} to avoid blocking the webhook
 * processing thread and to ensure entities are committed before we read them.
 *
 * <p>Filtering is layered: this listener rejects events that are not valid data (sync replays,
 * terminal-state PRs, missing entity or branch info) before any policy is consulted, and
 * {@link PracticeReviewDetectionGate} decides whether the workspace actually wants a review.
 *
 * <p>Only active when the agent job queue is enabled.
 */
@Component
@ConditionalOnProperty(prefix = "hephaestus.agent", name = "enabled", havingValue = "true")
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
    public void onPullRequestCreated(ScmDomainEvent.PullRequestCreated event) {
        handlePullRequestEvent(event.pullRequest(), event.context(), TriggerEventNames.PULL_REQUEST_CREATED);
    }

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPullRequestReady(ScmDomainEvent.PullRequestReady event) {
        handlePullRequestEvent(event.pullRequest(), event.context(), TriggerEventNames.PULL_REQUEST_READY);
    }

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPullRequestSynchronized(ScmDomainEvent.PullRequestSynchronized event) {
        handlePullRequestEvent(event.pullRequest(), event.context(), TriggerEventNames.PULL_REQUEST_SYNCHRONIZED);
    }

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onReviewSubmitted(ScmDomainEvent.ReviewSubmitted event) {
        handleReviewEvent(event.review(), event.context());
    }

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPullRequestMerged(ScmDomainEvent.PullRequestMerged event) {
        handleRetrospectivePullRequestEvent(
            event.pullRequest(),
            event.context(),
            TriggerEventNames.PULL_REQUEST_MERGED
        );
    }

    /**
     * A merge publishes BOTH {@code PullRequestClosed(wasMerged=true)} and {@code PullRequestMerged}, so
     * this handler routes only the abandoned-close case and leaves the landing to
     * {@link #onPullRequestMerged}.
     */
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPullRequestClosed(ScmDomainEvent.PullRequestClosed event) {
        if (event.wasMerged()) {
            return;
        }
        handleRetrospectivePullRequestEvent(
            event.pullRequest(),
            event.context(),
            TriggerEventNames.PULL_REQUEST_CLOSED
        );
    }

    // PR event handling

    private void handlePullRequestEvent(
        ScmEventPayload.PullRequestData prData,
        EventContext context,
        String triggerEventName
    ) {
        // Agent reviews are for real-time activity only.
        if (context.isSync()) {
            return;
        }

        if (isClosedOrMerged(prData.state(), prData.isMerged())) {
            return;
        }

        try {
            PullRequest pr = pullRequestRepository.findByIdWithAllForGate(prData.id()).orElse(null);
            if (pr == null) {
                log.warn("Cannot submit agent job: PR not found, prId={}", prData.id());
                return;
            }

            if (!hasBranchInfo(pr, prData.id())) {
                return;
            }

            switch (practiceReviewDetectionGate.evaluate(pr, triggerEventName, TriggerMode.AUTO)) {
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

    // Retrospective PR event handling (merged / closed-without-merge)

    /**
     * Routes a terminal-state PR event through the same gate as the live handlers, deliberately without
     * their closed/merged short-circuit: here the terminal state IS the trigger's reason to run.
     */
    private void handleRetrospectivePullRequestEvent(
        ScmEventPayload.PullRequestData prData,
        EventContext context,
        String triggerEventName
    ) {
        // A sync would replay EVERY historical merge as a fresh retrospective review.
        if (context.isSync()) {
            return;
        }

        try {
            PullRequest pr = pullRequestRepository.findByIdWithAllForGate(prData.id()).orElse(null);
            if (pr == null) {
                log.warn("Cannot submit retrospective agent job: PR not found, prId={}", prData.id());
                return;
            }

            // A merged PR keeps its stored refs, so the guard still holds post-merge: without them there
            // is nothing to clone or diff against.
            if (!hasBranchInfo(pr, prData.id())) {
                return;
            }

            switch (practiceReviewDetectionGate.evaluate(pr, triggerEventName, TriggerMode.AUTO)) {
                case GateDecision.Skip skip -> log.debug(
                    "Retrospective agent job skipped by practice gate: prNumber={}, repoName={}, event={}, reason={}",
                    prData.number(),
                    prData.repository().nameWithOwner(),
                    triggerEventName,
                    skip.reason()
                );
                case GateDecision.Detect detect -> submitJob(prData, pr, detect, triggerEventName);
            }
        } catch (Exception e) {
            log.error(
                "Failed to process retrospective PR event: prNumber={}, repoName={}, event={}",
                prData.number(),
                prData.repository().nameWithOwner(),
                triggerEventName,
                e
            );
        }
    }

    // Review event handling

    private void handleReviewEvent(ScmEventPayload.ReviewData reviewData, EventContext context) {
        if (context.isSync()) {
            return;
        }

        try {
            // ReviewSubmitted carries ReviewData, not PullRequestData, so the PR is loaded here.
            PullRequest pr = pullRequestRepository.findByIdWithAllForGate(reviewData.pullRequestId()).orElse(null);
            if (pr == null) {
                log.warn(
                    "Cannot submit agent job for review: PR not found, reviewId={}, pullRequestId={}",
                    reviewData.id(),
                    reviewData.pullRequestId()
                );
                return;
            }

            // Reviews can arrive on already-merged PRs (drive-by reviews).
            if (isClosedOrMerged(pr.getState(), pr.isMerged())) {
                return;
            }

            if (!hasBranchInfo(pr, pr.getId())) {
                return;
            }

            switch (practiceReviewDetectionGate.evaluate(pr, TriggerEventNames.REVIEW_SUBMITTED, TriggerMode.AUTO)) {
                case GateDecision.Skip skip -> log.debug(
                    "Agent job skipped by practice gate for review: reviewId={}, prId={}, reason={}",
                    reviewData.id(),
                    pr.getId(),
                    skip.reason()
                );
                case GateDecision.Detect detect -> {
                    ScmEventPayload.PullRequestData prData = ScmEventPayload.PullRequestData.from(pr);
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

    // Shared helpers

    private static boolean isClosedOrMerged(Issue.State state, boolean merged) {
        return state == Issue.State.CLOSED || state == Issue.State.MERGED || merged;
    }

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
        ScmEventPayload.PullRequestData prData,
        PullRequest pr,
        GateDecision.Detect detect,
        String triggerEventName
    ) {
        PullRequestReviewSubmissionRequest request = new PullRequestReviewSubmissionRequest(
            prData,
            pr.getHeadRefName(),
            pr.getHeadRefOid(),
            pr.getBaseRefName(),
            triggerEventName
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
