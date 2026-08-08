package de.tum.cit.aet.hephaestus.agent.job;

import de.tum.cit.aet.hephaestus.agent.AgentJobType;
import de.tum.cit.aet.hephaestus.agent.handler.IssueReviewSubmissionRequest;
import de.tum.cit.aet.hephaestus.agent.handler.PullRequestReviewSubmissionRequest;
import de.tum.cit.aet.hephaestus.agent.handler.spi.JobSubmissionRequest;
import de.tum.cit.aet.hephaestus.core.AuditExempt;
import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import de.tum.cit.aet.hephaestus.integration.core.signal.SignalName;
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
 * Dev-only REST endpoint for manually triggering PR/issue reviews.
 * Enabled by setting hephaestus.dev.trigger-enabled=true.
 *
 * <p><strong>Instance admins only.</strong> The property is the switch that makes the endpoint exist;
 * it is not an access control. This route spends real LLM budget, so the property gates whether it is
 * mounted and {@code app_admin} gates who may call it — anything less and enabling the flag on a
 * reachable deployment hands unauthenticated callers a spend button bounded only by the monthly cap.
 *
 * <p>The artifact is loaded <em>through</em> the named workspace, not merely alongside it. Both are
 * request parameters and nothing about the two ids relates them, so an artifact fetched by surrogate id
 * alone and then submitted under whichever workspace was named would bill that workspace's
 * {@code agent_job} and LLM usage ledger for another's work — and the backfill cost estimator reads that
 * ledger, so the misattribution outlives the request.
 *
 * <p>Two modes:
 * <ul>
 *   <li><b>Bypass</b> (no {@code signal}): submits a review directly, skipping the detection gate.
 *       Use to force a review regardless of practice trigger config.</li>
 *   <li><b>Gate-routed</b> (with {@code signal}): loads the artifact and runs
 *       {@link PracticeReviewDetectionGate} with the given event name (e.g. {@code PullRequestMerged},
 *       {@code IssueClosed}) before submitting — exactly what the production listener would do. This is
 *       the ONLY way to validate RETROSPECTIVE (merged/closed) detection on a SYNCED mirror, where real
 *       merge/close webhooks never arrive and any sync-discovered terminal state is sync-skipped by the
 *       listeners.</li>
 * </ul>
 *
 * <p><strong>Transaction boundary:</strong> {@link AgentJobService#submit} must NOT run inside an outer
 * transaction, so the session-bound work (loading the artifact, the gate's lazy associations, building
 * the detached request) is confined to an explicit {@link TransactionTemplate} block and
 * {@link AgentJobService#submitPrepared} runs only after it commits.
 *
 * Usage:
 * <pre>
 *   POST /api/dev/trigger-review?prId=...&amp;workspaceId=...                              (bypass)
 *   POST /api/dev/trigger-review?prId=...&amp;workspaceId=...&amp;signal=scm.pull_request.merged  (gate)
 *   POST /api/dev/trigger-review?issueId=...&amp;workspaceId=...&amp;signal=scm.issue.closed      (gate)
 * </pre>
 */
@RestController
@ConditionalOnProperty(name = "hephaestus.dev.trigger-enabled", havingValue = "true")
@PreAuthorize("hasAuthority('app_admin')")
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
    @AuditExempt(
        reason = "submits a review job; changes no configuration or access, and the run and its spend are " +
            "already recorded on agent_job and the LLM usage ledger"
    )
    public String triggerReview(
        @RequestParam @Nullable Long prId,
        @RequestParam @Nullable Long issueId,
        @RequestParam @Nullable Long workspaceId,
        @RequestParam @Nullable String signal
    ) {
        if (workspaceId == null || (prId == null && issueId == null)) {
            return "Error: workspaceId and one of prId / issueId are required";
        }

        Prepared prepared = transactionTemplate.execute(status ->
            issueId != null ? prepareIssue(workspaceId, issueId, signal) : preparePullRequest(workspaceId, prId, signal)
        );

        if (prepared == null || prepared.request() == null) {
            return prepared == null ? "No submission prepared" : prepared.message();
        }
        // Null signal key on purpose. A key minted per click is a key that never repeats, and
        // AgentJobService only applies its in-flight deduplication when it has no key to trust
        // (`signalKey == null`) — so a per-click key does not make the trigger idempotent, it disables
        // the only deduplication this path has. Passing null restores it: a second click while the first
        // review is still running joins that run instead of paying for a second one.
        return agentJobService.submitPrepared(
            workspaceId,
            prepared.jobType(),
            (JobSubmissionRequest) prepared.request(),
            null
        );
    }

    private Prepared preparePullRequest(Long workspaceId, Long prId, @Nullable String signal) {
        // One answer for "no such PR" and "not this workspace's PR". Submitting it anyway would bill this
        // workspace's job and usage ledger for another one's artifact.
        PullRequest pr = artifactLoader.findPullRequestForGate(workspaceId, prId).orElse(null);
        if (pr == null) {
            return Prepared.done("PR not found in workspace " + workspaceId + ": " + prId);
        }
        SignalName triggerSignal = signal == null || signal.isBlank() ? null : SignalName.of(signal);
        if (triggerSignal != null) {
            GateDecision decision = detectionGate.evaluate(pr, triggerSignal, TriggerMode.AUTO);
            if (decision instanceof GateDecision.Skip skip) {
                return Prepared.done("Gate skipped (" + triggerSignal + "): " + skip.reason());
            }
        }
        PullRequestReviewSubmissionRequest request = agentJobService.buildReviewRequest(pr, triggerSignal);
        return request == null ? Prepared.done("PR missing branch info: prId=" + pr.getId()) : Prepared.review(request);
    }

    private Prepared prepareIssue(Long workspaceId, Long issueId, @Nullable String signal) {
        Issue issue = artifactLoader.findIssueForGate(workspaceId, issueId).orElse(null);
        if (issue == null) {
            return Prepared.done("Issue not found in workspace " + workspaceId + ": " + issueId);
        }
        SignalName triggerSignal = signal == null || signal.isBlank() ? null : SignalName.of(signal);
        if (triggerSignal != null) {
            GateDecision decision = detectionGate.evaluateIssue(issue, triggerSignal, TriggerMode.AUTO);
            if (decision instanceof GateDecision.Skip skip) {
                return Prepared.done("Gate skipped (" + triggerSignal + "): " + skip.reason());
            }
        }
        IssueReviewSubmissionRequest request = agentJobService.buildIssueRequest(issue, triggerSignal);
        return request == null
            ? Prepared.done("Issue missing repository: issueId=" + issue.getId())
            : Prepared.issue(request);
    }
}
