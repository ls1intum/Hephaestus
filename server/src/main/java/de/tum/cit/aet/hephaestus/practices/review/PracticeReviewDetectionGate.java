package de.tum.cit.aet.hephaestus.practices.review;

import de.tum.cit.aet.hephaestus.feature.FeatureFlag;
import de.tum.cit.aet.hephaestus.integration.scm.domain.issue.Issue;
import de.tum.cit.aet.hephaestus.integration.scm.domain.pullrequest.PullRequest;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.User;
import de.tum.cit.aet.hephaestus.practices.PracticeRepository;
import de.tum.cit.aet.hephaestus.practices.model.Practice;
import de.tum.cit.aet.hephaestus.practices.spi.AgentConfigChecker;
import de.tum.cit.aet.hephaestus.practices.spi.UserRoleChecker;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import de.tum.cit.aet.hephaestus.workspace.WorkspaceResolver;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;

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
 * <h2>Gate checks (in order)</h2>
 * <ol>
 *   <li>Draft PR + {@code skipDrafts} config → SKIP</li>
 *   <li>Workspace resolution → SKIP if not found</li>
 *   <li>Workspace {@code practicesEnabled} flag → SKIP if disabled (complete block)</li>
 *   <li>Trigger mode: auto-trigger or manual-trigger workspace setting → SKIP if disabled</li>
 *   <li>No enabled agent config for workspace → SKIP</li>
 *   <li>No active practices match trigger event → SKIP</li>
 *   <li>{@code runForAllUsers} config → DETECT (bypass role check)</li>
 *   <li>No assignee → SKIP</li>
 *   <li>Role checker unhealthy → SKIP</li>
 *   <li>Assignee has {@code run_practice_review} role → DETECT / SKIP</li>
 * </ol>
 */
@Service
public class PracticeReviewDetectionGate {

    private static final Logger log = LoggerFactory.getLogger(PracticeReviewDetectionGate.class);
    private static final String PRACTICE_REVIEW_ROLE = FeatureFlag.RUN_PRACTICE_REVIEW.key();
    private static final Duration SKIP_WARNING_INTERVAL = Duration.ofSeconds(30);

    private final PracticeReviewProperties properties;
    private final UserRoleChecker userRoleChecker;
    private final AgentConfigChecker agentConfigChecker;
    private final PracticeRepository practiceRepository;
    private final WorkspaceResolver workspaceResolver;

    private final AtomicLong skippedDueToUnhealthyCount = new AtomicLong(0);
    private final AtomicReference<Instant> lastSkipWarningTime = new AtomicReference<>(Instant.EPOCH);

    public PracticeReviewDetectionGate(
        PracticeReviewProperties properties,
        UserRoleChecker userRoleChecker,
        AgentConfigChecker agentConfigChecker,
        PracticeRepository practiceRepository,
        WorkspaceResolver workspaceResolver
    ) {
        this.properties = properties;
        this.userRoleChecker = userRoleChecker;
        this.agentConfigChecker = agentConfigChecker;
        this.practiceRepository = practiceRepository;
        this.workspaceResolver = workspaceResolver;
    }

    /**
     * Evaluates whether the practice review agent should run for the given PR event.
     * <p>
     * <strong>Transaction design:</strong> This method is intentionally NOT {@code @Transactional}.
     * Each DB read (workspace resolution, agent config check, practice query) runs in its own
     * transaction via Spring Data defaults / explicit annotation. The role check (step 7) is a
     * local DB lookup, so the gate holds no connection across an external call.
     *
     * @param pullRequest      the pull request (must have labels, assignees, repository eagerly loaded)
     * @param triggerEventName the domain event name (e.g., "PullRequestCreated", "ReviewSubmitted")
     * @return a {@link GateDecision} indicating whether to detect or skip (with reason)
     */
    public GateDecision evaluate(
        @NonNull PullRequest pullRequest,
        @NonNull String triggerEventName,
        @NonNull TriggerMode triggerMode
    ) {
        return evaluateReviewable(pullRequest, pullRequest.isDraft(), triggerEventName, triggerMode);
    }

    /**
     * Issue-side counterpart of {@link #evaluate}: runs the same workspace / feature / trigger-mode /
     * agent-config / practice-matching / role checks for an issue event. Practice matching filters by
     * the stored trigger-event strings, so only ISSUE-focused practices (which alone carry issue
     * trigger events) match — PR-only workspaces short-circuit with no extra cost. There is no draft
     * concept for issues, so that check is skipped.
     *
     * @param issue the issue (must have repository + assignees eagerly loaded)
     */
    public GateDecision evaluateIssue(
        @NonNull Issue issue,
        @NonNull String triggerEventName,
        @NonNull TriggerMode triggerMode
    ) {
        return evaluateReviewable(issue, false, triggerEventName, triggerMode);
    }

    private GateDecision evaluateReviewable(
        @NonNull Issue reviewable,
        boolean draft,
        @NonNull String triggerEventName,
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

        // 2. Draft gate (per-workspace skipDrafts override, falls back to the fleet property)
        if (workspace.getReviewSettings().resolveSkipDrafts(properties.skipDrafts()) && draft) {
            log.debug("Practice review gate: SKIP, reason=draftPR, prId={}", reviewable.getId());
            return new GateDecision.Skip("draft PR");
        }

        // 2a. Practices feature must be enabled for the workspace (complete block)
        if (!Boolean.TRUE.equals(workspace.getFeatures().getPracticesEnabled())) {
            log.debug(
                "Practice review gate: SKIP, reason=practicesDisabled, prId={}, workspaceId={}",
                reviewable.getId(),
                workspace.getId()
            );
            return new GateDecision.Skip("practices disabled for workspace");
        }

        // 2b. Trigger-mode-specific workspace setting
        if (
            triggerMode == TriggerMode.AUTO &&
            !Boolean.TRUE.equals(workspace.getFeatures().getPracticeReviewAutoTriggerEnabled())
        ) {
            log.debug(
                "Practice review gate: SKIP, reason=autoTriggerDisabled, prId={}, workspaceId={}",
                reviewable.getId(),
                workspace.getId()
            );
            return new GateDecision.Skip("auto-trigger disabled for workspace");
        }
        if (
            triggerMode == TriggerMode.MANUAL &&
            !Boolean.TRUE.equals(workspace.getFeatures().getPracticeReviewManualTriggerEnabled())
        ) {
            log.debug(
                "Practice review gate: SKIP, reason=manualTriggerDisabled, prId={}, workspaceId={}",
                reviewable.getId(),
                workspace.getId()
            );
            return new GateDecision.Skip("manual trigger disabled for workspace");
        }

        // 3. Agent config gate: at least one enabled agent config must exist
        if (!agentConfigChecker.hasEnabledConfig(workspace.getId())) {
            log.debug(
                "Practice review gate: SKIP, reason=noEnabledAgentConfig, prId={}, workspaceId={}",
                reviewable.getId(),
                workspace.getId()
            );
            return new GateDecision.Skip("no enabled agent config");
        }

        // 4. Practice matching: at least one active practice must match the trigger event
        List<Practice> matchedPractices = findMatchingPractices(workspace.getId(), triggerEventName);
        if (matchedPractices.isEmpty()) {
            log.debug(
                "Practice review gate: SKIP, reason=noMatchingPractices, prId={}, triggerEvent={}, workspaceId={}",
                reviewable.getId(),
                triggerEventName,
                workspace.getId()
            );
            return new GateDecision.Skip("no matching practices");
        }

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

    private List<Practice> findMatchingPractices(Long workspaceId, String triggerEventName) {
        return practiceRepository
            .findByWorkspaceIdAndActiveTrue(workspaceId)
            .stream()
            .filter(p -> containsTriggerEvent(p.getTriggerEvents(), triggerEventName))
            .toList();
    }

    private boolean containsTriggerEvent(JsonNode triggerEvents, String eventName) {
        if (triggerEvents == null || !triggerEvents.isArray()) {
            return false;
        }
        for (JsonNode node : triggerEvents) {
            if (eventName.equals(node.asString())) {
                return true;
            }
        }
        return false;
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
