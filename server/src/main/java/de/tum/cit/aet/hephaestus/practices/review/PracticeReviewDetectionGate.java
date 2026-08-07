package de.tum.cit.aet.hephaestus.practices.review;

import de.tum.cit.aet.hephaestus.feature.FeatureFlag;
import de.tum.cit.aet.hephaestus.integration.core.signal.SignalName;
import de.tum.cit.aet.hephaestus.integration.core.signal.SignalStateReason;
import de.tum.cit.aet.hephaestus.integration.scm.domain.issue.Issue;
import de.tum.cit.aet.hephaestus.integration.scm.domain.pullrequest.PullRequest;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.User;
import de.tum.cit.aet.hephaestus.practices.PracticeBinding;
import de.tum.cit.aet.hephaestus.practices.PracticeRepository;
import de.tum.cit.aet.hephaestus.practices.model.Practice;
import de.tum.cit.aet.hephaestus.practices.model.PracticeReviewTier;
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
 * Detection gate that decides whether to run the practice review agent for a given PR event.
 * <p>
 * Evaluates a series of checks — ordered cheap-to-expensive — to short-circuit before
 * sandbox execution, saving compute. The gate returns a {@link GateDecision} that either
 * carries the resolved workspace and matched practices ({@link GateDecision.Detect}) or
 * a skip reason ({@link GateDecision.Skip}).
 * <p>
 * <strong>Preconditions:</strong> The {@link PullRequest} must have labels, assignees,
 * and repository eagerly loaded before calling {@link #evaluate}.
 *
 * <p><strong>Two entry points, one order.</strong> {@link #evaluate} / {@link #evaluateIssue} take an SCM
 * artifact and run every step below. {@link #evaluateSignal} takes a workspace and a signal, for a kind
 * with no repository, no branch and no assignee — it runs steps 2a, 2b, 3 and 4, which are the ones that
 * ask nothing of the artifact. Both go through {@code evaluateWorkspaceAndSignal}, so the shared steps
 * cannot come to mean different things on the two paths.
 *
 * <h2>Gate checks (in order; numbers match the inline step comments)</h2>
 * <ol>
 *   <li>(1) Workspace resolution → SKIP if not found (first, so per-workspace settings drive the gates below)</li>
 *   <li>(2a) Workspace {@code practicesEnabled} flag → SKIP if disabled (complete block)</li>
 *   <li>(2a-bis) Workspace review scope (target branch / repository) → SKIP if the artifact is outside it</li>
 *   <li>(2b) Trigger mode: auto-trigger or manual-trigger workspace setting → SKIP if disabled</li>
 *   <li>(3) No runnable practice config for workspace → SKIP</li>
 *   <li>(4) No practice above tier {@code OFF} is bound to this signal in this draft state → SKIP</li>
 *   <li>(5) {@code runForAllUsers} setting → DETECT (bypass role check)</li>
 *   <li>(6) No assignee → SKIP</li>
 *   <li>(7) Role checker unhealthy → SKIP</li>
 *   <li>(8) Assignee has {@code run_practice_review} role → DETECT / SKIP</li>
 * </ol>
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

    private final AtomicLong skippedDueToUnhealthyCount = new AtomicLong(0);
    private final AtomicReference<Instant> lastSkipWarningTime = new AtomicReference<>(Instant.EPOCH);

    public PracticeReviewDetectionGate(
        PracticeReviewProperties properties,
        UserRoleChecker userRoleChecker,
        PracticeReviewReadiness practiceDetectionReadiness,
        PracticeRepository practiceRepository,
        WorkspaceResolver workspaceResolver
    ) {
        this.properties = properties;
        this.userRoleChecker = userRoleChecker;
        this.practiceDetectionReadiness = practiceDetectionReadiness;
        this.practiceRepository = practiceRepository;
        this.workspaceResolver = workspaceResolver;
    }

    /**
     * Evaluates whether the practice review agent should run for the given PR event.
     * <p>
     * <strong>Transaction design:</strong> This method is intentionally NOT {@code @Transactional}.
     * Each DB read (workspace resolution, agent config check, practice query) runs in its own
     * transaction via Spring Data defaults / explicit annotation. The role check (step 8) is a
     * local DB lookup, so the gate holds no connection across an external call.
     *
     * @param pullRequest the pull request (must have labels, assignees, repository eagerly loaded)
     * @param signal      the signal that occasioned this evaluation, e.g. {@code scm.pull_request.merged}
     * @return a {@link GateDecision} indicating whether to detect or skip (with reason)
     */
    public GateDecision evaluate(
        @NonNull PullRequest pullRequest,
        @NonNull SignalName signal,
        @NonNull TriggerMode triggerMode
    ) {
        return evaluateReviewable(pullRequest, pullRequest.isDraft(), signal, triggerMode);
    }

    /**
     * Issue-side counterpart of {@link #evaluate}: runs the same workspace / feature / trigger-mode /
     * agent-config / practice-matching / role checks for an issue event. Practice matching filters by
     * the bound signals, and a signal name carries its artifact kind, so only issue practices can match
     * an issue signal — PR-only workspaces short-circuit with no extra cost. An issue is never a draft.
     *
     * @param issue the issue (must have repository + assignees eagerly loaded)
     */
    public GateDecision evaluateIssue(
        @NonNull Issue issue,
        @NonNull SignalName signal,
        @NonNull TriggerMode triggerMode
    ) {
        return evaluateReviewable(issue, false, signal, triggerMode);
    }

    /**
     * The kind-generic half of the gate: everything that turns on the <em>workspace</em> and the
     * <em>signal</em>, for an artifact that has no repository, no branch and no assignee.
     *
     * <p>{@link #evaluate} and {@link #evaluateIssue} still take an SCM entity, because the checks they
     * add — review scope and the assignee role — read fields only an SCM artifact has. This method is what
     * a repo-less kind can reach: a document, a conversation thread, a pipeline run. It deliberately runs
     * the checks that decide <em>whether a review is wanted at all</em>, which is where the answers an
     * operator most needs told apart live: a workspace with no practice for this work, versus one that
     * bound a practice and turned it off.
     *
     * <p>What it does <em>not</em> do is check consent, because there is nothing kind-generic to check it
     * on. A repo-less kind names its subject itself, and the caller that resolved that subject is the one
     * that can answer for them.
     *
     * @param workspace the resolved workspace — supplied rather than looked up, since a repo-less caller
     *     already knows it (the signal ledger records the workspace on every row)
     */
    public GateDecision evaluateSignal(
        @NonNull Workspace workspace,
        @NonNull SignalName signal,
        @NonNull TriggerMode triggerMode
    ) {
        // Not a draft: draftness is a pull request's state, and a kind that has no such state must not be
        // filtered by the draft-specific half of a binding.
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
        // 1. Workspace resolution (first — per-workspace settings drive the draft gate below)
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

        // 2a-bis. Workspace review scope: which of this workspace's work is reviewed at all. ANDed onto
        //    every binding, because a binding names a signal and cannot name the trunk it fires against —
        //    a trunk is called main here and develop there, so a curated catalogue cannot know it. Computed
        //    here because only an SCM artifact has the repository and branch it reads, and applied by the
        //    shared gate in its original position: right after the feature flag, before anything that
        //    costs a query. It costs no query itself, so computing it eagerly changes nothing.
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

        // 2a / 2b / 3 / 4: everything that turns on the workspace and the signal rather than on the
        //    artifact. Shared with every repo-less kind through evaluateSignal.
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

        // 5. Run-for-all bypass: skip role check entirely (per-workspace override, falls back to property)
        if (workspace.getReviewSettings().resolveRunForAllUsers(properties.runForAllUsers())) {
            log.info(
                "Practice review gate: DETECT, reason=runForAllUsers, prId={}, matchedPractices={}",
                reviewable.getId(),
                matchedPractices.size()
            );
            return new GateDecision.Detect(workspace, matchedPractices);
        }

        // 6. Assignee gate: at least one assignee required for role checking
        var assignees = reviewable.getAssignees();
        if (assignees == null || assignees.isEmpty()) {
            log.debug("Practice review gate: SKIP, reason=noAssignee, prId={}", reviewable.getId());
            return new GateDecision.Skip("no assignee");
        }

        // 7. Role-checker health gate
        if (!userRoleChecker.isHealthy()) {
            logSkippedDueToUnhealthy(reviewable);
            return new GateDecision.Skip("role checker unhealthy");
        }

        // Reset skip counter on recovery
        long previousCount = skippedDueToUnhealthyCount.getAndSet(0);
        if (previousCount > 0) {
            log.info("Role checker recovered, resuming practice review gate checks");
        }

        // 8. Role check: DETECT if ANY assignee has the role
        return checkAssigneeRoles(reviewable, assignees, workspace, matchedPractices);
    }

    /**
     * Checks all assignees for the practice review role. Returns Detect on first match.
     * <p>
     * On exception: fails closed immediately (returns Skip) rather than continuing to the next
     * assignee. This is intentional — if the role checker is misbehaving, we should not
     * make additional calls. The {@code isHealthy()} gate (step 7) handles the common case;
     * this catch handles unexpected failures that slip through.
     */
    private GateDecision checkAssigneeRoles(
        Issue reviewable,
        Set<User> assignees,
        Workspace workspace,
        List<Practice> matchedPractices
    ) {
        for (User assignee : assignees) {
            try {
                // Identity is the stable (gitProviderId, subject) tuple, not the login: the role lives on
                // the Hephaestus account behind THIS provider's identity. subject == the provider's numeric
                // user id (User.nativeId as a string), matching IdentityLink.subject. A synced assignee
                // always carries both; guard defensively so a half-synced row fails safe (no role).
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
     * The workspace- and signal-level gate: the checks an artifact contributes nothing to.
     *
     * <p>Extracted so a repo-less kind reaches exactly the same decisions, in exactly the same order, as
     * a pull request does. The alternative — a second, simpler gate for kinds without a repository — is
     * how two paths come to disagree about what {@code OFF} means.
     *
     * @param subject an identifier for the logs only; the gate makes no decision from it
     * @param scopeSkip the artifact-level review-scope refusal, already computed by an SCM caller, or
     *     {@code null} when the artifact has no repository or branch for a scope to be about
     * @return {@link GateDecision.Detect} carrying the admitted practices, or the first refusal
     */
    private GateDecision evaluateWorkspaceAndSignal(
        Workspace workspace,
        SignalName signal,
        boolean draft,
        TriggerMode triggerMode,
        String subject,
        GateDecision.@Nullable Skip scopeSkip
    ) {
        // 2a. Practices feature must be enabled for the workspace (complete block)
        if (!Boolean.TRUE.equals(workspace.getFeatures().getPracticesEnabled())) {
            log.debug(
                "Practice review gate: SKIP, reason=practicesDisabled, subject={}, workspaceId={}",
                subject,
                workspace.getId()
            );
            return new GateDecision.Skip("practices disabled for workspace");
        }

        // 2a-bis. The artifact's own scope refusal, in the position it has always been evaluated in.
        if (scopeSkip != null) {
            return scopeSkip;
        }

        // 2b. Trigger-mode-specific workspace setting
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

        // 3. Agent gate: skip rather than incur LLM cost for a detection run that would submit no jobs.
        if (!practiceDetectionReadiness.hasRunnableAgent(workspace.getId())) {
            log.debug(
                "Practice review gate: SKIP, reason=noRunnableDetectionAgent, subject={}, workspaceId={}",
                subject,
                workspace.getId()
            );
            return new GateDecision.Skip("no runnable practice-review agent");
        }

        // 4. Practice matching: at least one selected practice must be bound to this signal, and — when
        //    the artifact is still a draft — bound to it on drafts. This replaced a fleet-wide draft
        //    veto that ran ahead of everything: with drafts skipped by default, the draft-specific
        //    criteria several practices are largely made of could never be reached. Whether a draft is
        //    worth reviewing is a property of the occasion, so the binding is where it is now answered.
        //    Admission then applies the loudness tier: OFF is the one tier that stops the review, so
        //    MEASURE and COACH still get here and still record their observations.
        SignalMatch match = findMatchingPractices(workspace.getId(), signal, draft);
        if (match.admitted().isEmpty()) {
            // A practice bound to this signal but turned all the way down is a deliberate act, not an
            // empty catalogue; recording the two under one reason would make them unanswerable apart.
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
     * The practices this signal occasions, split by whether their tier lets a review start.
     *
     * @param admitted the practices to review — bound to the signal and above {@link PracticeReviewTier#OFF}
     * @param silencedByTier whether at least one practice WAS bound to the signal and sat at {@code OFF},
     *     which is what lets the caller record "deliberately silenced" rather than "nothing bound"
     */
    private record SignalMatch(List<Practice> admitted, boolean silencedByTier) {}

    private SignalMatch findMatchingPractices(Long workspaceId, SignalName signal, boolean draft) {
        List<Practice> bound = practiceRepository
            .findByWorkspaceId(workspaceId)
            .stream()
            .filter(p ->
                p
                    .getBindings()
                    .stream()
                    .anyMatch(binding -> binding.occasionedBy(signal, draft))
            )
            .toList();
        List<Practice> admitted = bound
            .stream()
            .filter(p -> p.getReviewTier().admitsReview())
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
