package de.tum.cit.aet.hephaestus.practices.review;

import de.tum.cit.aet.hephaestus.integration.core.signal.SignalName;
import de.tum.cit.aet.hephaestus.integration.core.signal.SignalStateReason;
import de.tum.cit.aet.hephaestus.integration.scm.domain.issue.Issue;
import de.tum.cit.aet.hephaestus.integration.scm.domain.pullrequest.PullRequest;
import de.tum.cit.aet.hephaestus.practices.PracticeBinding;
import de.tum.cit.aet.hephaestus.practices.PracticeRepository;
import de.tum.cit.aet.hephaestus.practices.PracticeSignalOptions;
import de.tum.cit.aet.hephaestus.practices.model.Practice;
import de.tum.cit.aet.hephaestus.practices.model.PracticeAutonomy;
import de.tum.cit.aet.hephaestus.practices.review.autonomy.AutonomyResolver;
import de.tum.cit.aet.hephaestus.practices.spi.PracticeReviewReadiness;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import de.tum.cit.aet.hephaestus.workspace.WorkspaceResolver;
import java.util.List;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Decides whether to run the practice review agent for a PR or issue event, running checks ordered
 * cheap-to-expensive to short-circuit before sandbox execution. The reviewable's repository must be
 * loaded before calling {@link #evaluate}; audience selection belongs to delivery, not detection.
 *
 * <p>{@link #evaluateSignal} is the entry point for a kind with no repository, branch or assignee; it
 * shares the workspace/signal steps below with the reviewable path via {@code evaluateWorkspaceAndSignal}.
 */
@Service
public class PracticeReviewDetectionGate {

    private static final Logger log = LoggerFactory.getLogger(PracticeReviewDetectionGate.class);
    private final PracticeReviewReadiness practiceDetectionReadiness;
    private final PracticeRepository practiceRepository;
    private final WorkspaceResolver workspaceResolver;
    private final PracticeSignalOptions signalOptions;
    private final PracticeReviewCoverageService coverageService;

    public PracticeReviewDetectionGate(
        PracticeReviewReadiness practiceDetectionReadiness,
        PracticeRepository practiceRepository,
        WorkspaceResolver workspaceResolver,
        PracticeSignalOptions signalOptions,
        PracticeReviewCoverageService coverageService
    ) {
        this.practiceDetectionReadiness = practiceDetectionReadiness;
        this.practiceRepository = practiceRepository;
        this.workspaceResolver = workspaceResolver;
        this.signalOptions = signalOptions;
        this.coverageService = coverageService;
    }

    public GateDecision evaluate(
        @NonNull PullRequest pullRequest,
        @NonNull SignalName signal,
        @NonNull TriggerMode triggerMode
    ) {
        return evaluateReviewable(pullRequest, pullRequest.isDraft(), signal, triggerMode);
    }

    /**
     * Issue-side counterpart of {@link #evaluate}. A signal name carries its artifact kind, so only
     * issue practices can match an issue signal; an issue is never a draft.
     */
    public GateDecision evaluateIssue(
        @NonNull Issue issue,
        @NonNull SignalName signal,
        @NonNull TriggerMode triggerMode
    ) {
        return evaluateReviewable(issue, false, signal, triggerMode);
    }

    /**
     * The kind-generic half of the gate, for an artifact that has no repository, branch or assignee.
     *
     * <p>Consent is <em>not</em> checked here: there is nothing kind-generic to check it on, so the
     * caller that resolved the subject is the one that must answer for them.
     */
    public GateDecision evaluateSignal(
        @NonNull Workspace workspace,
        @NonNull SignalName signal,
        @NonNull TriggerMode triggerMode
    ) {
        // Never a draft: draftness is a pull request's state, so the draft half of a binding must not
        // filter a kind that has no such state.
        return evaluateWorkspaceAndSignal(
            workspace,
            signal,
            false,
            triggerMode,
            "workspace:" + workspace.getId(),
            null
        );
    }

    private GateDecision evaluateReviewable(
        @NonNull Issue reviewable,
        boolean draft,
        @NonNull SignalName signal,
        @NonNull TriggerMode triggerMode
    ) {
        // Workspace resolution first: per-workspace settings drive the checks below.
        String nameWithOwner =
            reviewable.getRepository() != null ? reviewable.getRepository().getNameWithOwner() : null;
        Workspace workspace = workspaceResolver.resolveForRepository(nameWithOwner).orElse(null);
        if (workspace == null) {
            log.debug(
                "Practice review gate: SKIP, reason=noWorkspace, prId={}, repo={}",
                reviewable.getId(),
                nameWithOwner
            );
            return new GateDecision.Skip("no workspace");
        }

        // Administrative evaluations bypass coverage but cannot deliver externally.
        GateDecision.@Nullable Skip scopeSkip = null;
        String targetBranch = reviewable instanceof PullRequest pr ? pr.getBaseRefName() : null;
        if (
            triggerMode != TriggerMode.ADMINISTRATIVE &&
            !(reviewable instanceof PullRequest
                ? coverageService.admits(workspace, nameWithOwner, targetBranch, reviewable.reviewSubject())
                : coverageService.admits(workspace, nameWithOwner, null, reviewable.reviewSubject(), false))
        ) {
            log.debug(
                "Practice review gate: SKIP, reason=outsideCoverage, artifactId={}, repo={}, targetBranch={}, authorId={}",
                reviewable.getId(),
                nameWithOwner,
                targetBranch,
                reviewable.reviewSubject().actorId()
            );
            scopeSkip = new GateDecision.Skip(
                "the repository, branch, or linked author is outside review coverage",
                reviewable.reviewSubject().actorId() == null
                    ? SignalStateReason.SUBJECT_UNLINKED
                    : SignalStateReason.OUT_OF_REVIEW_SCOPE
            );
        }

        // Workspace- and signal-level checks, shared with evaluateSignal.
        GateDecision shared = evaluateWorkspaceAndSignal(
            workspace,
            signal,
            draft,
            triggerMode,
            String.valueOf(reviewable.getId()),
            scopeSkip
        );
        if (shared instanceof GateDecision.Skip) {
            return shared;
        }
        return shared;
    }

    /**
     * The workspace- and signal-level gate: the checks an artifact contributes nothing to. Shared rather
     * than duplicated for repo-less kinds, so the two paths cannot disagree about what {@code OFF} means.
     *
     * @param subject an identifier for the logs only; the gate makes no decision from it
     */
    private GateDecision evaluateWorkspaceAndSignal(
        Workspace workspace,
        SignalName signal,
        boolean draft,
        TriggerMode triggerMode,
        String subject,
        GateDecision.@Nullable Skip scopeSkip
    ) {
        if (!Boolean.TRUE.equals(workspace.getFeatures().getPracticesEnabled())) {
            log.debug(
                "Practice review gate: SKIP, reason=practicesDisabled, subject={}, workspaceId={}",
                subject,
                workspace.getId()
            );
            return new GateDecision.Skip("practices disabled for workspace");
        }

        // The artifact's own scope refusal: checked after the feature flag but before anything that costs
        // a query.
        if (scopeSkip != null) {
            return scopeSkip;
        }

        if (
            triggerMode == TriggerMode.AUTO &&
            !Boolean.TRUE.equals(workspace.getFeatures().getPracticeReviewAutoTriggerEnabled())
        ) {
            log.debug(
                "Practice review gate: SKIP, reason=autoTriggerDisabled, subject={}, workspaceId={}",
                subject,
                workspace.getId()
            );
            return new GateDecision.Skip("auto-trigger disabled for workspace");
        }
        if (
            triggerMode == TriggerMode.MANUAL &&
            !Boolean.TRUE.equals(workspace.getFeatures().getPracticeReviewManualTriggerEnabled())
        ) {
            log.debug(
                "Practice review gate: SKIP, reason=manualTriggerDisabled, subject={}, workspaceId={}",
                subject,
                workspace.getId()
            );
            return new GateDecision.Skip("manual trigger disabled for workspace");
        }

        // Skip rather than incur LLM cost for a detection run that would submit no jobs.
        if (!practiceDetectionReadiness.hasRunnableAgent(workspace.getId())) {
            log.debug(
                "Practice review gate: SKIP, reason=noRunnableDetectionAgent, subject={}, workspaceId={}",
                subject,
                workspace.getId()
            );
            return new GateDecision.Skip("no runnable practice-review agent");
        }

        // A binding decides its own draft policy rather than a fleet-wide veto ahead of it, otherwise no
        // binding could ever fire on a draft.
        SignalMatch match = findMatchingPractices(workspace, signal, draft);
        if (match.admitted().isEmpty()) {
            // Two reasons, not one: "bound and turned all the way down" is a deliberate act and must stay
            // answerable apart from "nothing bound".
            if (match.hasDisabledPractice()) {
                log.debug(
                    "Practice review gate: SKIP, reason=allBoundPracticesOff, subject={}, signal={}, workspaceId={}",
                    subject,
                    signal,
                    workspace.getId()
                );
                return new GateDecision.Skip(
                    "every practice bound to this signal is off",
                    SignalStateReason.PRACTICE_AUTONOMY_OFF
                );
            }
            log.debug(
                "Practice review gate: SKIP, reason=noMatchingPractices, subject={}, signal={}, draft={}, " +
                    "workspaceId={}",
                subject,
                signal,
                draft,
                workspace.getId()
            );
            return new GateDecision.Skip(
                draft ? "no practices bound to this signal on drafts" : "no matching practices"
            );
        }
        return new GateDecision.Detect(
            workspace,
            match.admitted(),
            workspace.getReviewSettings().getRolloutRevision(),
            triggerMode,
            triggerMode != TriggerMode.ADMINISTRATIVE
        );
    }

    /**
     * @param admitted the practices to review — bound to the signal and above {@link PracticeAutonomy#OFF}
     * @param hasDisabledPractice at least one practice was bound to the signal and sat at {@code OFF}; this is
     *     what lets the caller record "deliberately silenced" rather than "nothing bound"
     */
    private record SignalMatch(List<Practice> admitted, boolean hasDisabledPractice) {}

    /**
     * The practices a signal occasions. A manual request is matched differently: it admits every practice
     * bound to the artifact's kind, ignoring both the specific signal and the draft filter, since asking
     * "review this now" has already answered the draft question. {@code OFF} still means off either way.
     */
    private SignalMatch findMatchingPractices(Workspace workspace, SignalName signal, boolean draft) {
        boolean requestedByHand = signalOptions.isManualRequest(signal);
        List<Practice> bound = practiceRepository
            .findByWorkspaceId(workspace.getId())
            .stream()
            .filter(p ->
                p
                    .getBindings()
                    .stream()
                    .anyMatch(binding ->
                        requestedByHand ? binding.appliesTo(signal.artifactKind()) : binding.occasionedBy(signal, draft)
                    )
            )
            .toList();
        // Reading the autonomy column raw would ask a practice that holds no opinion for one; resolve it
        // through practice -> area -> workspace instead.
        PracticeAutonomy workspaceDefault = WorkspaceReviewDefaults.of(workspace).defaultAutonomy();
        List<Practice> admitted = bound
            .stream()
            .filter(p -> AutonomyResolver.effectiveAutonomyOf(p, workspaceDefault).admitsReview())
            .toList();
        return new SignalMatch(admitted, admitted.isEmpty() && !bound.isEmpty());
    }
}
