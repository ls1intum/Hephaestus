package de.tum.cit.aet.hephaestus.agent.job;

import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import de.tum.cit.aet.hephaestus.integration.scm.domain.issue.Issue;
import de.tum.cit.aet.hephaestus.integration.scm.domain.pullrequest.PullRequest;
import de.tum.cit.aet.hephaestus.practices.review.GateDecision;
import de.tum.cit.aet.hephaestus.practices.review.PracticeReviewDetectionGate;
import de.tum.cit.aet.hephaestus.practices.review.TriggerMode;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
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

    public DevTriggerController(
        AgentJobService agentJobService,
        ReviewableArtifactLoader artifactLoader,
        PracticeReviewDetectionGate detectionGate
    ) {
        this.agentJobService = agentJobService;
        this.artifactLoader = artifactLoader;
        this.detectionGate = detectionGate;
    }

    @PostMapping("/api/dev/trigger-review")
    // The gate touches the lazy Workspace.reviewSettings proxy off the loaded PR/Issue (exactly as the
    // production @Transactional listeners do); without an open session the gate-routed path throws
    // LazyInitializationException. Keep the whole entry transactional so both the gate and submission share it.
    @Transactional
    public String triggerReview(
        @RequestParam @Nullable Long prId,
        @RequestParam @Nullable Long issueId,
        @RequestParam @Nullable Long workspaceId,
        @RequestParam @Nullable String triggerEvent
    ) {
        if (workspaceId == null || (prId == null && issueId == null)) {
            return "Error: workspaceId and one of prId / issueId are required";
        }
        if (triggerEvent != null && !triggerEvent.isBlank()) {
            return issueId != null
                ? triggerIssueThroughGate(workspaceId, issueId, triggerEvent)
                : triggerPullRequestThroughGate(workspaceId, prId, triggerEvent);
        }
        // Bypass mode (legacy): submit directly without the gate.
        if (issueId != null) {
            return agentJobService.submitDetectionForIssue(workspaceId, issueId);
        }
        return agentJobService.submitReviewForPullRequest(workspaceId, prId);
    }

    /**
     * Loads the PR, runs the detection gate with {@code triggerEvent} (TriggerMode.AUTO — the same mode the
     * production retrospective listener uses, so the workspace's auto-trigger toggle and practice trigger
     * matching are exercised exactly as in real-time), then submits on Detect. Closed/merged PRs are NOT
     * rejected — that terminal state is the reason a retrospective trigger runs.
     */
    private String triggerPullRequestThroughGate(Long workspaceId, Long prId, String triggerEvent) {
        PullRequest pr = artifactLoader.findPullRequestForGate(prId).orElse(null);
        if (pr == null) {
            return "PR not found: " + prId;
        }
        GateDecision decision = detectionGate.evaluate(pr, triggerEvent, TriggerMode.AUTO);
        return switch (decision) {
            case GateDecision.Skip skip -> "Gate skipped (" + triggerEvent + "): " + skip.reason();
            case GateDecision.Detect detect -> "Gate matched " +
            detect.matchedPractices().size() +
            " practice(s) for " +
            triggerEvent +
            " -> " +
            agentJobService.submitReviewForLoadedPullRequest(workspaceId, pr, triggerEvent);
        };
    }

    /** Issue-side counterpart of {@link #triggerPullRequestThroughGate}. */
    private String triggerIssueThroughGate(Long workspaceId, Long issueId, String triggerEvent) {
        Issue issue = artifactLoader.findIssueForGate(issueId).orElse(null);
        if (issue == null) {
            return "Issue not found: " + issueId;
        }
        GateDecision decision = detectionGate.evaluateIssue(issue, triggerEvent, TriggerMode.AUTO);
        return switch (decision) {
            case GateDecision.Skip skip -> "Gate skipped (" + triggerEvent + "): " + skip.reason();
            case GateDecision.Detect detect -> "Gate matched " +
            detect.matchedPractices().size() +
            " practice(s) for " +
            triggerEvent +
            " -> " +
            agentJobService.submitDetectionForLoadedIssue(workspaceId, issue, triggerEvent);
        };
    }
}
