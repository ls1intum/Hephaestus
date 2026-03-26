package de.tum.in.www1.hephaestus.practices.review;

import com.fasterxml.jackson.databind.JsonNode;
import de.tum.in.www1.hephaestus.feature.FeatureFlag;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequest;
import de.tum.in.www1.hephaestus.gitprovider.user.User;
import de.tum.in.www1.hephaestus.practices.PracticeRepository;
import de.tum.in.www1.hephaestus.practices.model.Practice;
import de.tum.in.www1.hephaestus.practices.spi.AgentConfigChecker;
import de.tum.in.www1.hephaestus.practices.spi.UserRoleChecker;
import de.tum.in.www1.hephaestus.workspace.Workspace;
import de.tum.in.www1.hephaestus.workspace.WorkspaceResolver;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
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
 * <h2>Gate checks (in order)</h2>
 * <ol>
 *   <li>{@code no-ai-review} label → SKIP</li>
 *   <li>Draft PR + {@code skipDrafts} config → SKIP</li>
 *   <li>Workspace resolution → SKIP if not found</li>
 *   <li>No enabled agent config for workspace → SKIP</li>
 *   <li>No active practices match trigger event → SKIP</li>
 *   <li>{@code runForAllUsers} config → DETECT (bypass role check)</li>
 *   <li>No assignee → SKIP</li>
 *   <li>Keycloak circuit breaker open → SKIP</li>
 *   <li>Assignee has {@code run_practice_review} role → DETECT / SKIP</li>
 * </ol>
 */
@Service
public class PracticeReviewDetectionGate {

    private static final Logger log = LoggerFactory.getLogger(PracticeReviewDetectionGate.class);
    private static final String PRACTICE_REVIEW_ROLE = FeatureFlag.RUN_PRACTICE_REVIEW.key();
    private static final String NO_AI_REVIEW_LABEL = "no-ai-review";
    private static final Duration SKIP_WARNING_INTERVAL = Duration.ofSeconds(30);

    private final PracticeReviewProperties properties;
    private final UserRoleChecker userRoleChecker;
    private final AgentConfigChecker agentConfigChecker;
    private final PracticeRepository practiceRepository;
    private final WorkspaceResolver workspaceResolver;

    private final AtomicLong skippedDueToKeycloakCount = new AtomicLong(0);
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
     * transaction via Spring Data defaults / explicit annotation. This avoids holding a DB
     * connection during the Keycloak HTTP calls in steps 8–9.
     *
     * @param pullRequest      the pull request (must have labels, assignees, repository eagerly loaded)
     * @param triggerEventName the domain event name (e.g., "PullRequestCreated", "ReviewSubmitted")
     * @return a {@link GateDecision} indicating whether to detect or skip (with reason)
     */
    public GateDecision evaluate(@NonNull PullRequest pullRequest, @NonNull String triggerEventName) {
        // 1. Label gate: no-ai-review label takes absolute precedence
        if (hasNoAiReviewLabel(pullRequest)) {
            log.debug("Practice review gate: SKIP, reason=label:no-ai-review, prId={}", pullRequest.getId());
            return new GateDecision.Skip("label:no-ai-review");
        }

        // 2. Draft gate
        if (properties.skipDrafts() && pullRequest.isDraft()) {
            log.debug("Practice review gate: SKIP, reason=draftPR, prId={}", pullRequest.getId());
            return new GateDecision.Skip("draft PR");
        }

        // 3. Workspace resolution
        String nameWithOwner =
            pullRequest.getRepository() != null ? pullRequest.getRepository().getNameWithOwner() : null;
        Workspace workspace = workspaceResolver.resolveForRepository(nameWithOwner).orElse(null);
        if (workspace == null) {
            log.debug(
                "Practice review gate: SKIP, reason=noWorkspace, prId={}, repo={}",
                pullRequest.getId(),
                nameWithOwner
            );
            return new GateDecision.Skip("no workspace");
        }

        // 4. Agent config gate: at least one enabled agent config must exist
        if (!agentConfigChecker.hasEnabledConfig(workspace.getId())) {
            log.debug(
                "Practice review gate: SKIP, reason=noEnabledAgentConfig, prId={}, workspaceId={}",
                pullRequest.getId(),
                workspace.getId()
            );
            return new GateDecision.Skip("no enabled agent config");
        }

        // 5. Practice matching: at least one active practice must match the trigger event
        List<Practice> matchedPractices = findMatchingPractices(workspace.getId(), triggerEventName);
        if (matchedPractices.isEmpty()) {
            log.debug(
                "Practice review gate: SKIP, reason=noMatchingPractices, prId={}, triggerEvent={}, workspaceId={}",
                pullRequest.getId(),
                triggerEventName,
                workspace.getId()
            );
            return new GateDecision.Skip("no matching practices");
        }

        // 6. Run-for-all bypass: skip role check entirely
        if (properties.runForAllUsers()) {
            log.info(
                "Practice review gate: DETECT, reason=runForAllUsers, prId={}, matchedPractices={}",
                pullRequest.getId(),
                matchedPractices.size()
            );
            return new GateDecision.Detect(workspace, matchedPractices);
        }

        // 7. Assignee gate: at least one assignee required for role checking
        var assignees = pullRequest.getAssignees();
        if (assignees == null || assignees.isEmpty()) {
            log.debug("Practice review gate: SKIP, reason=noAssignee, prId={}", pullRequest.getId());
            return new GateDecision.Skip("no assignee");
        }

        // 8. Keycloak health gate
        if (!userRoleChecker.isHealthy()) {
            logSkippedDueToKeycloak(pullRequest);
            return new GateDecision.Skip("keycloak circuit breaker open");
        }

        // Reset skip counter on recovery
        long previousCount = skippedDueToKeycloakCount.getAndSet(0);
        if (previousCount > 0) {
            log.info("Keycloak circuit breaker recovered, resuming practice review gate checks");
        }

        // 9. Role check: DETECT if ANY assignee has the role
        return checkAssigneeRoles(pullRequest, assignees, workspace, matchedPractices);
    }

    /**
     * Checks all assignees for the practice review role. Returns Detect on first match.
     * <p>
     * On exception: fails closed immediately (returns Skip) rather than continuing to the next
     * assignee. This is intentional — if the identity provider is misbehaving, we should not
     * make additional calls. The {@code isHealthy()} gate (step 8) handles the common case;
     * this catch handles unexpected failures that slip through the circuit breaker.
     */
    private GateDecision checkAssigneeRoles(
        PullRequest pullRequest,
        Set<User> assignees,
        Workspace workspace,
        List<Practice> matchedPractices
    ) {
        for (User assignee : assignees) {
            try {
                if (userRoleChecker.hasRole(assignee.getLogin(), PRACTICE_REVIEW_ROLE)) {
                    log.info(
                        "Practice review gate: DETECT, reason=hasRole, prId={}, userLogin={}, matchedPractices={}",
                        pullRequest.getId(),
                        assignee.getLogin(),
                        matchedPractices.size()
                    );
                    return new GateDecision.Detect(workspace, matchedPractices);
                }
            } catch (Exception e) {
                log.warn(
                    "Practice review gate: role check failed, prId={}, userLogin={}, error={}",
                    pullRequest.getId(),
                    assignee.getLogin(),
                    e.getMessage()
                );
                return new GateDecision.Skip("role check failed");
            }
        }

        log.debug(
            "Practice review gate: SKIP, reason=noAssigneeWithRole, prId={}, role={}",
            pullRequest.getId(),
            PRACTICE_REVIEW_ROLE
        );
        return new GateDecision.Skip("no assignee with role: " + PRACTICE_REVIEW_ROLE);
    }

    private boolean hasNoAiReviewLabel(PullRequest pullRequest) {
        if (pullRequest.getLabels() == null) {
            return false;
        }
        return pullRequest
            .getLabels()
            .stream()
            .anyMatch(label -> NO_AI_REVIEW_LABEL.equalsIgnoreCase(label.getName()));
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
            if (eventName.equals(node.asText())) {
                return true;
            }
        }
        return false;
    }

    private void logSkippedDueToKeycloak(PullRequest pullRequest) {
        long currentCount = skippedDueToKeycloakCount.incrementAndGet();
        Instant now = Instant.now();
        Instant lastWarning = lastSkipWarningTime.get();

        log.debug(
            "Practice review gate: SKIP, reason=keycloakCircuitBreakerOpen, prId={}, skippedCount={}",
            pullRequest.getId(),
            currentCount
        );

        // Rate-limit WARN logging to avoid log spam during Keycloak outages
        if (Duration.between(lastWarning, now).compareTo(SKIP_WARNING_INTERVAL) >= 0) {
            if (lastSkipWarningTime.compareAndSet(lastWarning, now)) {
                log.warn(
                    "Practice review gate skipping due to Keycloak circuit breaker open: skippedCount={}",
                    currentCount
                );
            }
        }
    }
}
