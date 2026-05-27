package de.tum.cit.aet.hephaestus.integration.slack.connect;

import de.tum.cit.aet.hephaestus.integration.core.connection.ConnectionConfig;
import de.tum.cit.aet.hephaestus.integration.core.connection.ConnectionService;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.slack.messaging.SlackMessageService;
import de.tum.cit.aet.hephaestus.integration.slack.messaging.SlackSendException;
import de.tum.cit.aet.hephaestus.workspace.authorization.RequireAtLeastWorkspaceAdmin;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Slack-specific admin endpoints — channel configuration + connectivity probe.
 *
 * <p>Lives next to {@link SlackConnectionStrategy} (per-kind admin surface) rather than
 * inflating {@code ConnectionController} with a kind-specific subroute. The DTOs are
 * deliberately flat records — no sealed-type discriminators — so springdoc 3.0 emits
 * concrete schemas into {@code openapi.yaml} and the generated webapp client picks
 * them up cleanly.
 */
@RestController
@RequestMapping("/api/v1/workspaces/{workspaceId}/connections/slack")
@RequireAtLeastWorkspaceAdmin
@ConditionalOnProperty(name = "hephaestus.integration.slack.enabled", havingValue = "true", matchIfMissing = false)
public class SlackConnectionAdminController {

    private static final Logger log = LoggerFactory.getLogger(SlackConnectionAdminController.class);

    /** Slack channel ids start with C (public), G (private), or D (DM); 9+ chars total. */
    private static final java.util.regex.Pattern SLACK_CHANNEL_ID_PATTERN = java.util.regex.Pattern.compile(
        "^[CGD][A-Z0-9]{8,}$"
    );

    private final ConnectionService connectionService;
    private final SlackMessageService slackMessageService;

    public SlackConnectionAdminController(
        ConnectionService connectionService,
        SlackMessageService slackMessageService
    ) {
        this.connectionService = connectionService;
        this.slackMessageService = slackMessageService;
    }

    /**
     * Set the Slack notification channel + optional team label on the workspace's
     * ACTIVE Slack Connection. Returns the updated {@link SlackNotificationConfigResponse}.
     *
     * @throws ResponseStatusException 400 if the channel id format is invalid;
     *                                 404 if no ACTIVE Slack Connection exists for the workspace.
     */
    @PatchMapping("/notification-channel")
    public ResponseEntity<SlackNotificationConfigResponse> updateNotificationChannel(
        @PathVariable Long workspaceId,
        @jakarta.validation.Valid @RequestBody SlackNotificationChannelRequest body
    ) {
        String channelId = body == null ? null : body.channelId();
        String teamLabel = body == null ? null : body.teamLabel();
        if (channelId != null && !channelId.isBlank() && !SLACK_CHANNEL_ID_PATTERN.matcher(channelId).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid Slack channel ID format");
        }
        var updated = connectionService.updateConfig(workspaceId, IntegrationKind.SLACK, cfg -> {
            if (!(cfg instanceof ConnectionConfig.SlackConfig slack)) {
                throw new IllegalStateException(
                    "Expected SlackConfig on workspace=" +
                        workspaceId +
                        " but got " +
                        (cfg == null ? "null" : cfg.getClass().getSimpleName())
                );
            }
            String nextChannel = blankToNull(channelId);
            String nextTeam = blankToNull(teamLabel);
            return new ConnectionConfig.SlackConfig(
                slack.teamId(),
                slack.teamName(),
                nextChannel,
                nextTeam,
                slack.enabledStreams()
            );
        });
        if (updated.isEmpty()) {
            throw new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "No ACTIVE Slack Connection for workspace=" + workspaceId
            );
        }
        ConnectionConfig.SlackConfig slack = (ConnectionConfig.SlackConfig) updated.get().getConfig();
        log.info(
            "Updated Slack notification config: workspaceId={}, channelSet={}, teamLabelSet={}",
            workspaceId,
            slack.notificationChannelId() != null,
            slack.teamLabel() != null
        );
        return ResponseEntity.ok(SlackNotificationConfigResponse.from(slack));
    }

    /**
     * Post a quick "Hephaestus test message" to the configured channel. 404 when no
     * Slack Connection exists or the channel isn't configured. 502 on Slack-side error
     * with the Slack error code in the body.
     */
    @PostMapping("/test-message")
    public ResponseEntity<SlackTestMessageResponse> sendTestMessage(@PathVariable Long workspaceId) {
        var config = connectionService
            .findSlackNotificationConfig(workspaceId)
            .orElseThrow(() ->
                new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "No ACTIVE Slack Connection for workspace=" + workspaceId
                )
            );
        String channelId = config.notificationChannelId();
        if (channelId == null || channelId.isBlank()) {
            throw new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Slack notification channel not configured for workspace=" + workspaceId
            );
        }
        try {
            slackMessageService.sendForWorkspace(
                workspaceId,
                channelId,
                List.of(),
                "Hephaestus test message — your Slack integration is wired up."
            );
            return ResponseEntity.ok(new SlackTestMessageResponse(true, channelId, null));
        } catch (SlackSendException e) {
            log.warn(
                "Slack test message failed: workspaceId={}, channelId={}, error={}",
                workspaceId,
                channelId,
                e.slackError()
            );
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(
                new SlackTestMessageResponse(false, channelId, e.slackError())
            );
        }
    }

    @Nullable
    private static String blankToNull(@Nullable String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
