package de.tum.cit.aet.hephaestus.agent.job;

import de.tum.cit.aet.hephaestus.agent.AgentJobType;
import de.tum.cit.aet.hephaestus.agent.handler.IssueReviewSubmissionRequest;
import de.tum.cit.aet.hephaestus.agent.handler.PullRequestReviewSubmissionRequest;
import de.tum.cit.aet.hephaestus.agent.handler.spi.JobSubmissionRequest;
import de.tum.cit.aet.hephaestus.integration.core.events.ScmEventPayload;
import de.tum.cit.aet.hephaestus.integration.core.signal.ArtifactKind;
import de.tum.cit.aet.hephaestus.integration.core.signal.DiscoveredVia;
import de.tum.cit.aet.hephaestus.integration.core.signal.SignalKey;
import de.tum.cit.aet.hephaestus.integration.core.signal.SignalName;
import de.tum.cit.aet.hephaestus.integration.core.signal.SignalRecorder;
import de.tum.cit.aet.hephaestus.integration.core.signal.SignalStateReason;
import de.tum.cit.aet.hephaestus.integration.scm.domain.issue.Issue;
import de.tum.cit.aet.hephaestus.integration.scm.domain.pullrequest.PullRequest;
import de.tum.cit.aet.hephaestus.integration.scm.domain.signal.ScmSignals;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.User;
import de.tum.cit.aet.hephaestus.practices.PracticeSignalOptions;
import de.tum.cit.aet.hephaestus.practices.model.ObservationOrigin;
import de.tum.cit.aet.hephaestus.practices.review.GateDecision;
import de.tum.cit.aet.hephaestus.practices.review.PracticeReviewDetectionGate;
import de.tum.cit.aet.hephaestus.practices.review.TriggerMode;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import java.time.Instant;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The one path a review somebody asked for takes, whichever front door they asked through.
 *
 * <p>Two doors exist today — the {@code /hephaestus review} command on a merge request and the REST
 * endpoint the web app's "Review this now" calls — and both have to do the same five things in the same
 * order: establish that the asker has standing, ask the workspace's gate whether a review of this kind
 * of work runs at all, record the ask in the signal ledger so the artifact trace can say who asked and
 * what came of it, submit, and hand back a refusal a person can read. Duplicating that sequence is how
 * one door ends up authorizing and the other not, which is exactly the state this class was written to
 * end.
 *
 * <h2>The occasion is the request, and it is recorded as one</h2>
 * <p>The gate is asked about the kind's declared manual-request signal — {@code
 * scm.pull_request.review_requested} and its issue counterpart — not about some lifecycle event that
 * did not happen. The artifact trace renders the signal as the reason a review ran, so naming an event
 * nobody observed puts an untruth in the one place a developer goes to find out why.
 *
 * <h2>The job's metadata carries no signal, deliberately</h2>
 * <p>The ledger key names the request; the submission request does not. That asymmetry is the contract
 * {@code PracticeCatalogInjector} already documents: a job with no trigger signal in its metadata runs
 * every active practice of the artifact's kind, which is what a person asking "review this now" means.
 * Putting the request signal in the metadata instead would have the injector filter practices by a
 * signal no practice binds to, and the job would fail to prepare having found none.
 *
 * <h2>Origin</h2>
 * <p>{@link ObservationOrigin#MANUAL}, passed explicitly rather than left to the submission request's
 * default. The default reads the trigger signal, so it is right only for as long as the metadata signal
 * stays null; stating it here means a later change to that contract cannot quietly file a self-selected
 * sample into the population the LIVE trend line is read from.
 */
@Service
public class ManualReviewRequests {

    private static final Logger log = LoggerFactory.getLogger(ManualReviewRequests.class);

    private final ReviewRequestAuthority authority;
    private final PracticeReviewDetectionGate gate;
    private final PracticeSignalOptions signalOptions;
    private final SignalRecorder signalRecorder;
    private final AgentJobService agentJobService;
    private final TransactionTemplate transactionTemplate;

    public ManualReviewRequests(
        ReviewRequestAuthority authority,
        PracticeReviewDetectionGate gate,
        PracticeSignalOptions signalOptions,
        SignalRecorder signalRecorder,
        AgentJobService agentJobService,
        TransactionTemplate transactionTemplate
    ) {
        this.authority = authority;
        this.gate = gate;
        this.signalOptions = signalOptions;
        this.signalRecorder = signalRecorder;
        this.agentJobService = agentJobService;
        this.transactionTemplate = transactionTemplate;
    }

    /**
     * Ask for a review of a pull request now.
     *
     * <p><strong>Must not be called inside a transaction</strong> — {@link AgentJobService#submit} opens
     * its own and says why. The pull request's associations (author, assignees, repository, branch refs)
     * must already be fetched; nothing here reopens a session to read them.
     */
    public ManualReviewOutcome requestPullRequestReview(
        Workspace workspace,
        PullRequest pullRequest,
        @Nullable User requester
    ) {
        if (!authority.mayRequest(workspace.getId(), pullRequest, requester)) {
            log.info(
                "Manual review request refused: no standing on the artifact, workspaceId={}, prId={}, requesterId={}",
                workspace.getId(),
                pullRequest.getId(),
                requester == null ? null : requester.getId()
            );
            return ManualReviewOutcome.forbidden();
        }
        if (
            pullRequest.getHeadRefOid() == null ||
            pullRequest.getHeadRefName() == null ||
            pullRequest.getBaseRefName() == null
        ) {
            // Nothing to clone or diff. Reported as the artifact being gone rather than as a gate skip:
            // the mirror has not caught up with the branch, and it is not the workspace that declined.
            return ManualReviewOutcome.refused(SignalStateReason.ARTIFACT_GONE);
        }
        return run(
            workspace,
            pullRequest,
            requester,
            signal -> gate.evaluate(pullRequest, signal, TriggerMode.MANUAL),
            AgentJobType.PULL_REQUEST_REVIEW,
            () ->
                new PullRequestReviewSubmissionRequest(
                    ScmEventPayload.PullRequestData.from(pullRequest),
                    pullRequest.getHeadRefName(),
                    pullRequest.getHeadRefOid(),
                    pullRequest.getBaseRefName(),
                    null,
                    ObservationOrigin.MANUAL
                )
        );
    }

    /** Issue-shaped counterpart of {@link #requestPullRequestReview}, with the same preconditions. */
    public ManualReviewOutcome requestIssueReview(Workspace workspace, Issue issue, @Nullable User requester) {
        if (!authority.mayRequest(workspace.getId(), issue, requester)) {
            log.info(
                "Manual review request refused: no standing on the artifact, workspaceId={}, issueId={}, requesterId={}",
                workspace.getId(),
                issue.getId(),
                requester == null ? null : requester.getId()
            );
            return ManualReviewOutcome.forbidden();
        }
        if (issue.getRepository() == null) {
            return ManualReviewOutcome.refused(SignalStateReason.ARTIFACT_GONE);
        }
        return run(
            workspace,
            issue,
            requester,
            signal -> gate.evaluateIssue(issue, signal, TriggerMode.MANUAL),
            AgentJobType.ISSUE_REVIEW,
            () ->
                new IssueReviewSubmissionRequest(
                    issue.getId(),
                    issue.getNumber(),
                    issue.getRepository().getId(),
                    issue.getRepository().getNameWithOwner(),
                    issue.getTitle(),
                    issue.getBody() != null ? issue.getBody() : "",
                    issue.getState() != null ? issue.getState().name() : "OPEN",
                    issue.getHtmlUrl(),
                    issue.getUpdatedAt(),
                    null,
                    ObservationOrigin.MANUAL
                )
        );
    }

    /** Which signal this kind says a person raises by asking, the gate, the ledger, the submission. */
    private ManualReviewOutcome run(
        Workspace workspace,
        Issue artifact,
        @Nullable User requester,
        Function<SignalName, GateDecision> evaluate,
        AgentJobType jobType,
        Supplier<JobSubmissionRequest> submission
    ) {
        ArtifactKind kind = AgentJobService.artifactKindFor(jobType);
        SignalName requestSignal = signalOptions.manualRequestSignalFor(kind).orElse(null);
        if (requestSignal == null) {
            // The kind never declared one, so there is no occasion to record this under and no vocabulary
            // to explain it in. Refusing beats inventing a signal name the descriptor does not know.
            log.warn("Manual review request on a kind that declares none: kind={}", kind);
            return ManualReviewOutcome.refused(SignalStateReason.NO_ACTIVE_PRACTICE);
        }

        // A fresh run id per ask, which is what makes two people asking two occasions rather than one
        // deduplicated against the other — and what keeps a request from consuming the ledger entry an
        // ordinary event was going to use.
        SignalKey key = ScmSignals.manualKey(workspace.getId(), artifact.getId(), requestSignal, UUID.randomUUID());
        transactionTemplate.executeWithoutResult(status ->
            signalRecorder.record(key, Instant.now(), DiscoveredVia.MANUAL)
        );

        GateDecision decision = evaluate.apply(requestSignal);
        if (decision instanceof GateDecision.Skip skip) {
            log.info(
                "Manual review request: gate declined, workspaceId={}, artifactId={}, reason={}",
                workspace.getId(),
                artifact.getId(),
                skip.reason()
            );
            SignalStateReason reason = skip.resolvedSignalReason();
            transactionTemplate.executeWithoutResult(status -> signalRecorder.markRefused(key, reason));
            return ManualReviewOutcome.refused(reason);
        }

        SubmissionOutcome outcome = agentJobService.submitWithOutcome(
            workspace.getId(),
            jobType,
            submission.get(),
            key
        );
        if (outcome.job() == null) {
            return ManualReviewOutcome.refused(outcome.requireRefusal());
        }
        log.info(
            "Manual review request: submitted, jobId={}, workspaceId={}, artifactId={}, requesterId={}",
            outcome.job().getId(),
            workspace.getId(),
            artifact.getId(),
            requester == null ? null : requester.getId()
        );
        return ManualReviewOutcome.submitted(outcome.job().getId());
    }
}
