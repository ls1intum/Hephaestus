package de.tum.cit.aet.hephaestus.agent.job;

import de.tum.cit.aet.hephaestus.agent.AgentJobType;
import de.tum.cit.aet.hephaestus.agent.handler.IssueReviewSubmissionRequest;
import de.tum.cit.aet.hephaestus.agent.handler.PullRequestReviewSubmissionRequest;
import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import de.tum.cit.aet.hephaestus.integration.scm.domain.issue.Issue;
import de.tum.cit.aet.hephaestus.integration.scm.domain.pullrequest.PullRequest;
import de.tum.cit.aet.hephaestus.practices.review.GateDecision;
import de.tum.cit.aet.hephaestus.practices.review.PracticeReviewDetectionGate;
import de.tum.cit.aet.hephaestus.practices.review.TriggerMode;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Dev-only REST endpoint for manually triggering PR/issue reviews without NATS.
 * Enabled by setting hephaestus.dev.trigger-enabled=true.
 *
 * <p>Two modes:
 * <ul>
 *   <li><b>Bypass</b> (no {@code triggerEvent}): submits a review directly, skipping the detection gate.
 *       Use to force a review regardless of practice trigger config.</li>
 *   <li><b>Gate-routed</b> (with {@code triggerEvent}): loads the artifact and runs
 *       {@link PracticeReviewDetectionGate} with the given event name (e.g. {@code PullRequestMerged},
 *       {@code IssueClosed}) before submitting — exactly what the production listener would do. This is
 *       the ONLY way to validate RETROSPECTIVE (merged/closed) detection on a SYNCED mirror, where real
 *       merge/close webhooks never arrive and any sync-discovered terminal state is sync-skipped by the
 *       listeners.</li>
 * </ul>
 *
 * <p><strong>Transaction boundary:</strong> {@link AgentJobService#submit} contracts that it
 * must NOT run inside an outer transaction (each config gets its own transaction for isolation). The
 * session-bound work — loading the artifact, the gate's lazy {@code Workspace.reviewSettings} access, and
 * building the detached request — therefore runs inside an explicit {@link TransactionTemplate} block, and
 * {@link AgentJobService#submitPrepared} (which calls {@code submit}) runs only AFTER that block commits.
 *
 * Usage:
 * <pre>
 *   POST /api/dev/trigger-review?prId=...&amp;workspaceId=...                              (bypass)
 *   POST /api/dev/trigger-review?prId=...&amp;workspaceId=...&amp;triggerEvent=PullRequestMerged  (gate)
 *   POST /api/dev/trigger-review?issueId=...&amp;workspaceId=...&amp;triggerEvent=IssueClosed      (gate)
 * </pre>
 */
@RestController
@ConditionalOnProperty(name = "hephaestus.dev.trigger-enabled", havingValue = "true")
@PreAuthorize("permitAll()")
@WorkspaceAgnostic("Dev-only endpoint; workspace ID passed as request parameter")
public class DevTriggerController {

    private final AgentJobService agentJobService;
    private final ReviewableArtifactLoader artifactLoader;
    private final PracticeReviewDetectionGate detectionGate;
    private final TransactionTemplate transactionTemplate;

    public DevTriggerController(
        AgentJobService agentJobService,
        ReviewableArtifactLoader artifactLoader,
        PracticeReviewDetectionGate detectionGate,
        TransactionTemplate transactionTemplate
    ) {
        this.agentJobService = agentJobService;
        this.artifactLoader = artifactLoader;
        this.detectionGate = detectionGate;
        this.transactionTemplate = transactionTemplate;
    }

    /** Outcome of the session-bound prep phase: a request ready to submit, or a terminal message. */
    private record Prepared(@Nullable AgentJobType jobType, @Nullable Object request, @Nullable String message) {
        static Prepared review(@Nullable PullRequestReviewSubmissionRequest request) {
            return new Prepared(AgentJobType.PULL_REQUEST_REVIEW, request, null);
        }

        static Prepared issue(@Nullable IssueReviewSubmissionRequest request) {
            return new Prepared(AgentJobType.ISSUE_REVIEW, request, null);
        }

        static Prepared done(String message) {
            return new Prepared(null, null, message);
        }
    }

    @PostMapping("/api/dev/trigger-review")
    public String triggerReview(
        @RequestParam @Nullable Long prId,
        @RequestParam @Nullable Long issueId,
        @RequestParam @Nullable Long workspaceId,
        @RequestParam @Nullable String triggerEvent
    ) {
        if (workspaceId == null || (prId == null && issueId == null)) {
            return "Error: workspaceId and one of prId / issueId are required";
        }

        // Phase 1 (transactional): load + optional gate + build the detached request. This needs an open
        // session for lazy associations; it deliberately does NOT call submit().
        Prepared prepared = transactionTemplate.execute(status ->
            issueId != null ? prepareIssue(issueId, triggerEvent) : preparePullRequest(prId, triggerEvent)
        );

        // Phase 2 (OUTSIDE the transaction): submit, honouring submit()'s no-outer-transaction contract.
        if (prepared == null || prepared.request() == null) {
            return prepared == null ? "No submission prepared" : prepared.message();
        }
        return agentJobService.submitPrepared(
            workspaceId,
            prepared.jobType(),
            (de.tum.cit.aet.hephaestus.agent.handler.spi.JobSubmissionRequest) prepared.request()
        );
    }

    private Prepared preparePullRequest(Long prId, @Nullable String triggerEvent) {
        PullRequest pr = artifactLoader.findPullRequestForGate(prId).orElse(null);
        if (pr == null) {
            return Prepared.done("PR not found: " + prId);
        }
        if (triggerEvent != null && !triggerEvent.isBlank()) {
            GateDecision decision = detectionGate.evaluate(pr, triggerEvent, TriggerMode.AUTO);
            if (decision instanceof GateDecision.Skip skip) {
                return Prepared.done("Gate skipped (" + triggerEvent + "): " + skip.reason());
            }
        }
        PullRequestReviewSubmissionRequest request = agentJobService.buildReviewRequest(pr, triggerEvent);
        return request == null ? Prepared.done("PR missing branch info: prId=" + pr.getId()) : Prepared.review(request);
    }

    private Prepared prepareIssue(Long issueId, @Nullable String triggerEvent) {
        Issue issue = artifactLoader.findIssueForGate(issueId).orElse(null);
        if (issue == null) {
            return Prepared.done("Issue not found: " + issueId);
        }
        if (triggerEvent != null && !triggerEvent.isBlank()) {
            GateDecision decision = detectionGate.evaluateIssue(issue, triggerEvent, TriggerMode.AUTO);
            if (decision instanceof GateDecision.Skip skip) {
                return Prepared.done("Gate skipped (" + triggerEvent + "): " + skip.reason());
            }
        }
        IssueReviewSubmissionRequest request = agentJobService.buildIssueRequest(issue, triggerEvent);
        return request == null
            ? Prepared.done("Issue missing repository: issueId=" + issue.getId())
            : Prepared.issue(request);
    }
}
