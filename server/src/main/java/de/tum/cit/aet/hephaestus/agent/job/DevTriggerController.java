package de.tum.cit.aet.hephaestus.agent.job;

import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.access.prepost.PreAuthorize;
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
 *   <li><b>Gate-routed</b> (with {@code triggerEvent}): loads the artifact and runs the detection gate with
 *       the given event name (e.g. {@code PullRequestMerged}, {@code IssueClosed}) before submitting —
 *       exactly what the production listener would do. This is the ONLY way to validate RETROSPECTIVE
 *       (merged/closed) detection on a SYNCED mirror, where real merge/close webhooks never arrive and any
 *       sync-discovered terminal state is sync-skipped by the listeners.</li>
 * </ul>
 *
 * <p>The controller is intentionally <strong>not</strong> {@code @Transactional}: load + gate + request
 * building need an open session, but {@link AgentJobService#submit} contracts that it must run OUTSIDE any
 * outer transaction. {@link AgentJobService#devTriggerReview} / {@link AgentJobService#devTriggerIssueDetection}
 * own that split (session-bound prep inside a {@code TransactionTemplate}, submit after it commits).
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

    public DevTriggerController(AgentJobService agentJobService) {
        this.agentJobService = agentJobService;
    }

    @PostMapping("/api/dev/trigger-review")
    public String triggerReview(
        @RequestParam @Nullable Long prId,
        @RequestParam @Nullable Long issueId,
        @RequestParam @Nullable Long workspaceId,
        @RequestParam @Nullable String triggerEvent
    ) {
        if (workspaceId == null || (prId == null && issueId == null)) {
            return "Error: workspaceId and one of prId / issueId are required";
        }
        // Both bypass (triggerEvent null) and gate-routed modes are handled by the service, which keeps the
        // session-bound work transactional and runs submit() outside that transaction.
        return issueId != null
            ? agentJobService.devTriggerIssueDetection(workspaceId, issueId, triggerEvent)
            : agentJobService.devTriggerReview(workspaceId, prId, triggerEvent);
    }
}
