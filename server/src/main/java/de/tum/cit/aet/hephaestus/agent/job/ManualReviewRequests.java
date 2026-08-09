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
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;
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
 *
 * <h2>Order of the checks, which is load-bearing</h2>
 * <p>Standing, then the rate limits, then the ledger row, then the gate. Authorizing first means the
 * limits only ever count asks the product was willing to entertain, so a stranger cannot consume a
 * team's allowance by being refused repeatedly. Limiting before recording means a declined ask leaves
 * no row — see {@link ManualReviewRateLimits} for why counting one's own refusals would make the limit
 * tighten under retry. The gate comes last because it is the only step whose answer belongs in the
 * artifact's trace: by then there is a row for it to settle.
 */
@Service
public class ManualReviewRequests {

    private static final Logger log = LoggerFactory.getLogger(ManualReviewRequests.class);

    private final ReviewRequestAuthority authority;
    private final ManualReviewRateLimits rateLimits;
    private final PracticeReviewDetectionGate gate;
    private final PracticeSignalOptions signalOptions;
    private final SignalRecorder signalRecorder;
    private final AgentJobService agentJobService;
    private final TransactionTemplate transactionTemplate;

    public ManualReviewRequests(
        ReviewRequestAuthority authority,
        ManualReviewRateLimits rateLimits,
        PracticeReviewDetectionGate gate,
        PracticeSignalOptions signalOptions,
        SignalRecorder signalRecorder,
        AgentJobService agentJobService,
        TransactionTemplate transactionTemplate
    ) {
        this.authority = authority;
        this.rateLimits = rateLimits;
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
     *
     * @param requesters every SCM identity of the person asking. A collection rather than one identity
     *     because a Hephaestus account may link several, and asking the question of one of them at a
     *     time refuses an admin for signing in through the wrong provider. Empty means nobody was
     *     identified, which is itself a refusal.
     */
    public ManualReviewOutcome requestPullRequestReview(
        Workspace workspace,
        PullRequest pullRequest,
        Collection<User> requesters
    ) {
        User requester = authority.standingOf(workspace.getId(), pullRequest, requesters).orElse(null);
        if (requester == null) {
            log.info(
                "Manual review request refused: no standing on the artifact, workspaceId={}, prId={}, identities={}",
                workspace.getId(),
                pullRequest.getId(),
                requesters.size()
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
            new Asker(requester, identityIds(requesters)),
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
    public ManualReviewOutcome requestIssueReview(Workspace workspace, Issue issue, Collection<User> requesters) {
        User requester = authority.standingOf(workspace.getId(), issue, requesters).orElse(null);
        if (requester == null) {
            log.info(
                "Manual review request refused: no standing on the artifact, workspaceId={}, issueId={}, identities={}",
                workspace.getId(),
                issue.getId(),
                requesters.size()
            );
            return ManualReviewOutcome.forbidden();
        }
        if (issue.getRepository() == null) {
            return ManualReviewOutcome.refused(SignalStateReason.ARTIFACT_GONE);
        }
        return run(
            workspace,
            issue,
            new Asker(requester, identityIds(requesters)),
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

    /**
     * The person asking, as both halves of what a request needs to know about them.
     *
     * @param standing the identity the authority accepted, and the one the ledger row is filed under
     * @param identityIds every identity of that same person, which is what the hourly allowance counts —
     *     counting only the accepted one would hand a linked account one allowance per provider
     */
    private record Asker(User standing, List<Long> identityIds) {}

    /** Which signal this kind says a person raises by asking, the limits, the ledger, the gate, the job. */
    private ManualReviewOutcome run(
        Workspace workspace,
        Issue artifact,
        Asker asker,
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

        SignalStateReason limited = rateLimits
            .refusalFor(workspace, kind, artifact.getId(), asker.identityIds())
            .orElse(null);
        if (limited != null) {
            log.info(
                "Manual review request: rate limited, workspaceId={}, artifactId={}, requesterId={}, reason={}",
                workspace.getId(),
                artifact.getId(),
                asker.standing().getId(),
                limited
            );
            return ManualReviewOutcome.refused(limited);
        }

        // A fresh run id per ask, which is what makes two people asking two occasions rather than one
        // deduplicated against the other — and what keeps a request from consuming the ledger entry an
        // ordinary event was going to use.
        SignalKey key = ScmSignals.manualKey(workspace.getId(), artifact.getId(), requestSignal, UUID.randomUUID());
        transactionTemplate.executeWithoutResult(status ->
            signalRecorder.record(key, Instant.now(), DiscoveredVia.MANUAL, asker.standing().getId())
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
            asker.standing().getId()
        );
        return ManualReviewOutcome.submitted(outcome.job().getId());
    }

    /** Ids only: the limit counts rows, and a detached {@link User} is more than it needs to hold. */
    private static List<Long> identityIds(Collection<User> requesters) {
        return requesters.stream().map(User::getId).filter(Objects::nonNull).toList();
    }
}
