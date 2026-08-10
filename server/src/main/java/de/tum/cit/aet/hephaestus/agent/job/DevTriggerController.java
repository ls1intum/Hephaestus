package de.tum.cit.aet.hephaestus.agent.job;

import de.tum.cit.aet.hephaestus.agent.AgentJobType;
import de.tum.cit.aet.hephaestus.agent.handler.IssueReviewSubmissionRequest;
import de.tum.cit.aet.hephaestus.agent.handler.PullRequestReviewSubmissionRequest;
import de.tum.cit.aet.hephaestus.agent.handler.spi.JobSubmissionRequest;
import de.tum.cit.aet.hephaestus.core.AuditExempt;
import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import de.tum.cit.aet.hephaestus.integration.core.signal.DiscoveredVia;
import de.tum.cit.aet.hephaestus.integration.core.signal.SignalKey;
import de.tum.cit.aet.hephaestus.integration.core.signal.SignalName;
import de.tum.cit.aet.hephaestus.integration.core.signal.SignalRecorder;
import de.tum.cit.aet.hephaestus.integration.scm.domain.issue.Issue;
import de.tum.cit.aet.hephaestus.integration.scm.domain.pullrequest.PullRequest;
import de.tum.cit.aet.hephaestus.integration.scm.domain.signal.ScmSignals;
import de.tum.cit.aet.hephaestus.practices.review.GateDecision;
import de.tum.cit.aet.hephaestus.practices.review.PracticeReviewDetectionGate;
import de.tum.cit.aet.hephaestus.practices.review.TriggerMode;
import java.time.Instant;
import java.util.UUID;
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
 * <p>The artifact is loaded <em>through</em> the named workspace via {@link ReviewableArtifactLoader},
 * never by surrogate id alone — see that class for why the two request parameters must be joined.
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
    private final SignalRecorder signalRecorder;

    public DevTriggerController(
        AgentJobService agentJobService,
        ReviewableArtifactLoader artifactLoader,
        PracticeReviewDetectionGate detectionGate,
        TransactionTemplate transactionTemplate,
        SignalRecorder signalRecorder
    ) {
        this.agentJobService = agentJobService;
        this.artifactLoader = artifactLoader;
        this.detectionGate = detectionGate;
        this.transactionTemplate = transactionTemplate;
        this.signalRecorder = signalRecorder;
    }

    /**
     * Outcome of the session-bound prep phase: a request ready to submit, or a terminal message.
     *
     * @param signalKey the ledger identity this run is recorded under. Present for the gate-bypass mode,
     *     where the run has no signal of its own and is exactly what the manual-request vocabulary is
     *     for; null in gate-routed mode, where the caller named a real signal and
     *     {@code preparePullRequest} already keyed the ledger on it.
     */
    private record Prepared(
        @Nullable AgentJobType jobType,
        @Nullable Object request,
        @Nullable String message,
        @Nullable SignalKey signalKey
    ) {
        static Prepared review(@Nullable PullRequestReviewSubmissionRequest request, @Nullable SignalKey key) {
            return new Prepared(AgentJobType.PULL_REQUEST_REVIEW, request, null, key);
        }

        static Prepared issue(@Nullable IssueReviewSubmissionRequest request, @Nullable SignalKey key) {
            return new Prepared(AgentJobType.ISSUE_REVIEW, request, null, key);
        }

        static Prepared done(String message) {
            return new Prepared(null, null, message, null);
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
        // The bypass mode carries the kind's manual-request key, so the run this endpoint starts leaves
        // the same trace any other requested review does — and the artifact trace can answer "why did a
        // review run here" instead of showing a job with no occasion behind it.
        //
        // It used to pass null with the argument that a per-click key disables the in-flight
        // deduplication (AgentJobService only consults `findByWorkspaceIdAndIdempotencyKeyAndStatusIn`
        // when `signalKey == null`). That is half true and the wrong half: the partial unique index
        // `uk_agent_job_idempotency` still refuses the second concurrent insert on the same key, so a
        // double click is answered with CONCURRENT_DUPLICATE rather than silently paying twice. The only
        // thing lost is that the second click no longer reports the first click's job id, and it now
        // reports a sentence saying why instead.
        return agentJobService.submitPrepared(
            workspaceId,
            prepared.jobType(),
            (JobSubmissionRequest) prepared.request(),
            prepared.signalKey()
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
                recordRefusal(
                    ScmSignals.pullRequestKey(
                        workspaceId,
                        pr.getId(),
                        triggerSignal,
                        pr.getHeadRefOid(),
                        pr.getTitle(),
                        pr.getBody()
                    ).orElse(null),
                    skip
                );
                return Prepared.done("Gate skipped (" + triggerSignal + "): " + skip.reason());
            }
        }
        PullRequestReviewSubmissionRequest request = agentJobService.buildReviewRequest(pr, triggerSignal);
        return request == null
            ? Prepared.done("PR missing branch info: prId=" + pr.getId())
            : Prepared.review(
                  request,
                  triggerSignal == null
                      ? recordManualRequest(workspaceId, pr.getId(), ScmSignals.PULL_REQUEST_MANUAL_REVIEW)
                      : null
              );
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
                recordRefusal(
                    ScmSignals.issueKey(
                        workspaceId,
                        issue.getId(),
                        triggerSignal,
                        issue.getTitle(),
                        issue.getBody(),
                        issue.getUpdatedAt()
                    ).orElse(null),
                    skip
                );
                return Prepared.done("Gate skipped (" + triggerSignal + "): " + skip.reason());
            }
        }
        IssueReviewSubmissionRequest request = agentJobService.buildIssueRequest(issue, triggerSignal);
        return request == null
            ? Prepared.done("Issue missing repository: issueId=" + issue.getId())
            : Prepared.issue(
                  request,
                  triggerSignal == null
                      ? recordManualRequest(workspaceId, issue.getId(), ScmSignals.ISSUE_MANUAL_REVIEW)
                      : null
              );
    }

    /**
     * Open a ledger entry for a run this endpoint occasioned itself, and hand back its key.
     *
     * <p>A fresh run id per call, which is the whole point of the {@code RUN_ID} scheme: an ask is its
     * own occasion, so two of them are two rows rather than one deduplicated against the other, and
     * neither consumes the entry an ordinary lifecycle event was going to use.
     */
    private SignalKey recordManualRequest(long workspaceId, long artifactId, SignalName requestSignal) {
        SignalKey key = ScmSignals.manualKey(workspaceId, artifactId, requestSignal, UUID.randomUUID());
        signalRecorder.record(key, Instant.now(), DiscoveredVia.MANUAL);
        return key;
    }

    /**
     * Settle the ledger the way the production listener does, so a gate-routed trigger leaves the same
     * trace a real delivery would.
     *
     * <p>Without this the artifact keeps a signal that says only "recorded", and the trace answers a
     * reader with "no decision has been taken yet" for a review that ran, finished and was paid for —
     * telling them to wait for something already over. Recorded rather than the response alone
     * because the response is gone as soon as it is read.
     *
     * <p>Null key when the signal has nothing stable to be keyed on (a pull request whose head commit
     * the mirror does not have yet). There is then no ledger row this refusal belongs to, and inventing
     * one would assert an occurrence nobody observed.
     */
    private void recordRefusal(@Nullable SignalKey key, GateDecision.Skip skip) {
        if (key == null) {
            return;
        }
        signalRecorder.markRefused(key, skip.resolvedSignalReason());
    }
}
