package de.tum.cit.aet.hephaestus.practices.review;

import de.tum.cit.aet.hephaestus.feature.FeatureFlag;
import de.tum.cit.aet.hephaestus.integration.core.signal.SignalName;
import de.tum.cit.aet.hephaestus.integration.core.signal.SignalStateReason;
import de.tum.cit.aet.hephaestus.integration.scm.domain.issue.Issue;
import de.tum.cit.aet.hephaestus.integration.scm.domain.pullrequest.PullRequest;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.User;
import de.tum.cit.aet.hephaestus.practices.PracticeBinding;
import de.tum.cit.aet.hephaestus.practices.PracticeRepository;
import de.tum.cit.aet.hephaestus.practices.PracticeSignalOptions;
import de.tum.cit.aet.hephaestus.practices.model.Practice;
import de.tum.cit.aet.hephaestus.practices.model.PracticeReviewTier;
import de.tum.cit.aet.hephaestus.practices.model.PracticeReviewTier;
import de.tum.cit.aet.hephaestus.practices.review.tier.ReviewTierResolver;
import de.tum.cit.aet.hephaestus.practices.spi.PracticeReviewReadiness;
import de.tum.cit.aet.hephaestus.practices.spi.UserRoleChecker;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import de.tum.cit.aet.hephaestus.workspace.WorkspaceResolver;
import de.tum.cit.aet.hephaestus.workspace.settings.WorkspaceReviewScope;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Decides whether to run the practice review agent for a PR or issue event, running checks ordered
 * cheap-to-expensive to short-circuit before sandbox execution. Preconditions: the {@link PullRequest}
 * must have labels, assignees, and repository eagerly loaded before calling {@link #evaluate}.
 *
 * <p>{@link #evaluateSignal} is the entry point for a kind with no repository, branch or assignee; it
 * shares the workspace/signal steps below with the reviewable path via {@code evaluateWorkspaceAndSignal}.
 */
@Service
public class PracticeReviewDetectionGate {

    private static final Logger log = LoggerFactory.getLogger(PracticeReviewDetectionGate.class);
    private static final String PRACTICE_REVIEW_ROLE = FeatureFlag.RUN_PRACTICE_REVIEW.key();
    private static final Duration SKIP_WARNING_INTERVAL = Duration.ofSeconds(30);

    private final PracticeReviewProperties properties;
    private final UserRoleChecker userRoleChecker;
    private final PracticeReviewReadiness practiceDetectionReadiness;
    private final PracticeRepository practiceRepository;
    private final WorkspaceResolver workspaceResolver;
    private final PracticeSignalOptions signalOptions;

    private final AtomicLong skippedDueToUnhealthyCount = new AtomicLong(0);
    private final AtomicReference<Instant> lastSkipWarningTime = new AtomicReference<>(Instant.EPOCH);

    public PracticeReviewDetectionGate(
        PracticeReviewProperties properties,
        UserRoleChecker userRoleChecker,
        PracticeReviewReadiness practiceDetectionReadiness,
        PracticeRepository practiceRepository,
        WorkspaceResolver workspaceResolver,
        PracticeSignalOptions signalOptions
    ) {
        this.properties = properties;
        this.userRoleChecker = userRoleChecker;
        this.practiceDetectionReadiness = practiceDetectionReadiness;
        this.practiceRepository = practiceRepository;
        this.workspaceResolver = workspaceResolver;
        this.signalOptions = signalOptions;
    }

    /**
     * Deliberately not {@code @Transactional}: each DB read runs in its own transaction, so the gate
     * never holds a connection across the external role-check call in {@link #checkAssigneeRoles}.
     */
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

        // A binding names a signal, not the trunk it targets (that varies per workspace), so scope is
        // ANDed on here — the one place that has the artifact's repository and branch to check it against.
        GateDecision.@Nullable Skip scopeSkip = null;
        WorkspaceReviewScope scope = workspace.getReviewSettings().resolveReviewScope();
        if (!scope.isUnrestricted()) {
            // Only a pull request has a target branch; an issue passes the branch axis by construction.
            String targetBranch = reviewable instanceof PullRequest pr ? pr.getBaseRefName() : null;
            if (!scope.admits(nameWithOwner, targetBranch)) {
                log.debug(
                    "Practice review gate: SKIP, reason=outOfReviewScope, prId={}, repo={}, targetBranch={}",
                    reviewable.getId(),
                    nameWithOwner,
                    targetBranch
                );
                scopeSkip = new GateDecision.Skip(
                    "outside the workspace review scope",
                    SignalStateReason.OUT_OF_REVIEW_SCOPE
                );
            }
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
        List<Practice> matchedPractices = ((GateDecision.Detect) shared).matchedPractices();

        // Run-for-all bypass skips the role check entirely; falls back to the instance property.
        if (workspace.getReviewSettings().resolveRunForAllUsers(properties.runForAllUsers())) {
            log.info(
                "Practice review gate: DETECT, reason=runForAllUsers, prId={}, matchedPractices={}",
                reviewable.getId(),
                matchedPractices.size()
            );
            return new GateDecision.Detect(workspace, matchedPractices);
        }

        var assignees = reviewable.getAssignees();
        if (assignees == null || assignees.isEmpty()) {
            log.debug("Practice review gate: SKIP, reason=noAssignee, prId={}", reviewable.getId());
            return new GateDecision.Skip("no assignee");
        }

        if (!userRoleChecker.isHealthy()) {
            logSkippedDueToUnhealthy(reviewable);
            return new GateDecision.Skip("role checker unhealthy");
        }

        long previousCount = skippedDueToUnhealthyCount.getAndSet(0);
        if (previousCount > 0) {
            log.info("Role checker recovered, resuming practice review gate checks");
        }

        return checkAssigneeRoles(reviewable, assignees, workspace, matchedPractices);
    }

    /**
     * Fails closed on the first role-check exception rather than trying the next assignee — a
     * misbehaving role checker should not get more calls.
     */
    private GateDecision checkAssigneeRoles(
        Issue reviewable,
        Set<User> assignees,
        Workspace workspace,
        List<Practice> matchedPractices
    ) {
        for (User assignee : assignees) {
            try {
                // Role checks key on (gitProviderId, nativeId) — matching IdentityLink.subject, not the
                // login — so a half-synced assignee fails safe (no role) rather than throwing.
                var provider = assignee.getProvider();
                if (provider == null || provider.getId() == null || assignee.getNativeId() == null) {
                    continue;
                }
                if (
                    userRoleChecker.hasRole(
                        provider.getId(),
                        String.valueOf(assignee.getNativeId()),
                        PRACTICE_REVIEW_ROLE
                    )
                ) {
                    log.info(
                        "Practice review gate: DETECT, reason=hasRole, prId={}, userLogin={}, matchedPractices={}",
                        reviewable.getId(),
                        assignee.getLogin(),
                        matchedPractices.size()
                    );
                    return new GateDecision.Detect(workspace, matchedPractices);
                }
            } catch (Exception e) {
                log.warn(
                    "Practice review gate: role check failed, prId={}, userLogin={}, error={}",
                    reviewable.getId(),
                    assignee.getLogin(),
                    e.getMessage()
                );
                return new GateDecision.Skip("role check failed");
            }
        }

        log.debug(
            "Practice review gate: SKIP, reason=noAssigneeWithRole, prId={}, role={}",
            reviewable.getId(),
            PRACTICE_REVIEW_ROLE
        );
        return new GateDecision.Skip("no assignee with role: " + PRACTICE_REVIEW_ROLE);
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
            if (match.silencedByTier()) {
                log.debug(
                    "Practice review gate: SKIP, reason=allBoundPracticesOff, subject={}, signal={}, workspaceId={}",
                    subject,
                    signal,
                    workspace.getId()
                );
                return new GateDecision.Skip(
                    "every practice bound to this signal is off",
                    SignalStateReason.PRACTICE_TIER_OFF
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
        return new GateDecision.Detect(workspace, match.admitted());
    }

    /**
     * @param admitted the practices to review — bound to the signal and above {@link PracticeReviewTier#OFF}
     * @param silencedByTier at least one practice was bound to the signal and sat at {@code OFF}; this is
     *     what lets the caller record "deliberately silenced" rather than "nothing bound"
     */
    private record SignalMatch(List<Practice> admitted, boolean silencedByTier) {}

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
        // Reading the tier column raw would ask a practice that holds no opinion for one; resolve it
        // through practice -> area -> workspace instead.
        PracticeReviewTier workspaceDefault = WorkspaceReviewDefaults.of(workspace).defaultTier();
        List<Practice> admitted = bound
            .stream()
            .filter(p -> ReviewTierResolver.effectiveTierOf(p, workspaceDefault).admitsReview())
            .toList();
        return new SignalMatch(admitted, admitted.isEmpty() && !bound.isEmpty());
    }

    private void logSkippedDueToUnhealthy(Issue reviewable) {
        long currentCount = skippedDueToUnhealthyCount.incrementAndGet();
        Instant now = Instant.now();
        Instant lastWarning = lastSkipWarningTime.get();

        log.debug(
            "Practice review gate: SKIP, reason=roleCheckerUnhealthy, prId={}, skippedCount={}",
            reviewable.getId(),
            currentCount
        );

        // Rate-limit WARN logging to avoid log spam during role-checker outages
        if (Duration.between(lastWarning, now).compareTo(SKIP_WARNING_INTERVAL) >= 0) {
            if (lastSkipWarningTime.compareAndSet(lastWarning, now)) {
                log.warn("Practice review gate skipping due to role checker unhealthy: skippedCount={}", currentCount);
            }
        }
    }
}
