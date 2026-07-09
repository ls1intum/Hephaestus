package de.tum.cit.aet.hephaestus.integration.slack.channel;

import de.tum.cit.aet.hephaestus.core.runtime.ConditionalOnServerRole;
import de.tum.cit.aet.hephaestus.integration.slack.channel.SlackChannelConsentService.RegistrationOutcome;
import de.tum.cit.aet.hephaestus.workspace.authorization.RequireAtLeastWorkspaceAdmin;
import de.tum.cit.aet.hephaestus.workspace.context.WorkspaceContext;
import de.tum.cit.aet.hephaestus.workspace.context.WorkspaceScopedController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Per-workspace Slack channel activation control plane — the admin surface that makes a monitored channel's consent
 * reachable ({@code PENDING → ACTIVE ⇄ PAUSED → REVOKED}), mirroring the repo's resource-oriented lifecycle
 * convention ({@code WorkspaceController.updateStatus → WorkspaceLifecycleService.updateStatus}: a PATCH to the
 * target state driving a guarded, idempotent {@code switch}). Slack-workspace admin ≠ Hephaestus admin, so
 * activation lives in the webapp admin plane guarded by {@link RequireAtLeastWorkspaceAdmin}, never in a Slack modal.
 *
 * <p>Revocation (+ erasure) is expressed only as {@code PATCH consentState=REVOKED} — there is deliberately no
 * {@code DELETE}: it would be the identical transition under different semantics, and the row is NOT removed from
 * the collection (a REVOKED row documents that a channel was revoked, and {@code register()} resurrects it to
 * {@code PENDING} when an admin sets it up again).
 *
 * <p>The path variable is the Slack {@code C…}/{@code G…} channel id — the stable, non-enumerable natural key
 * {@code (workspace_id, slack_channel_id)}. Every method scopes on the {@link WorkspaceContext} workspace id, so a
 * channel of another workspace resolves to 404 (isolation). Illegal transitions surface as {@code 409 ProblemDetail}
 * through {@link SlackChannelControllerAdvice}; not-found / validation / auth flow through the shared advice chain.
 */
@WorkspaceScopedController
@RequestMapping("/slack/channels")
@RequireAtLeastWorkspaceAdmin
@ConditionalOnServerRole
@ConditionalOnProperty(name = "hephaestus.integration.slack.enabled", havingValue = "true", matchIfMissing = false)
@Validated
@Tag(name = "Slack channel activation", description = "Per-workspace Slack channel consent + activation")
public class SlackChannelAdminController {

    private final SlackChannelConsentService consentService;
    private final SlackChannelDirectoryService directoryService;

    public SlackChannelAdminController(
        SlackChannelConsentService consentService,
        SlackChannelDirectoryService directoryService
    ) {
        this.consentService = consentService;
        this.directoryService = directoryService;
    }

    @GetMapping
    @Operation(
        operationId = "listSlackChannels",
        summary = "List the workspace's allow-listed Slack channels with their consent state"
    )
    public ResponseEntity<List<SlackMonitoredChannelDTO>> listSlackChannels(WorkspaceContext workspace) {
        return ResponseEntity.ok(consentService.listChannels(workspace.id()));
    }

    @GetMapping("/candidates")
    @Operation(
        operationId = "listSlackChannelCandidates",
        summary = "List Slack channels available to add to monitoring"
    )
    public ResponseEntity<List<SlackChannelCandidateDTO>> listSlackChannelCandidates(WorkspaceContext workspace) {
        return ResponseEntity.ok(directoryService.listCandidates(workspace.id()));
    }

    @PostMapping
    @Operation(
        operationId = "registerSlackChannel",
        summary = "Allow-list a Slack channel (lands in PENDING; idempotent on the natural key)"
    )
    public ResponseEntity<SlackMonitoredChannelDTO> registerSlackChannel(
        WorkspaceContext workspace,
        @Valid @RequestBody RegisterSlackChannelRequestDTO request
    ) {
        RegistrationOutcome outcome = consentService.register(
            workspace.id(),
            request.slackChannelId(),
            request.channelName()
        );
        return ResponseEntity.status(outcome.created() ? HttpStatus.CREATED : HttpStatus.OK).body(outcome.channel());
    }

    @PatchMapping("/{slackChannelId}")
    @Operation(
        operationId = "updateSlackChannelConsent",
        summary = "Transition a Slack channel to a target consent state (activate / pause / resume / revoke)"
    )
    public ResponseEntity<SlackMonitoredChannelDTO> updateSlackChannelConsent(
        WorkspaceContext workspace,
        @PathVariable String slackChannelId,
        @Valid @RequestBody UpdateSlackChannelConsentRequestDTO request
    ) {
        return ResponseEntity.ok(
            consentService.transition(workspace.id(), slackChannelId, request.consentState(), request.reason())
        );
    }

    @GetMapping("/{slackChannelId}/consent-events")
    @Operation(
        operationId = "listSlackChannelConsentEvents",
        summary = "The immutable consent-transition audit trail of one Slack channel"
    )
    public ResponseEntity<List<SlackChannelConsentEventDTO>> listSlackChannelConsentEvents(
        WorkspaceContext workspace,
        @PathVariable String slackChannelId
    ) {
        return ResponseEntity.ok(consentService.listConsentEvents(workspace.id(), slackChannelId));
    }
}
