package de.tum.cit.aet.hephaestus.integration.slack.connect;

import de.tum.cit.aet.hephaestus.integration.core.connection.ConnectionService;
import de.tum.cit.aet.hephaestus.integration.slack.messaging.SlackMessageService;
import de.tum.cit.aet.hephaestus.integration.slack.messaging.SlackSendException;
import de.tum.cit.aet.hephaestus.workspace.authorization.RequireAtLeastWorkspaceAdmin;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Slack-specific admin endpoint — connectivity probe.
 *
 * <p>Channel + team configuration is owned by
 * {@code PATCH /workspaces/{slug}/notifications} (existing endpoint that writes
 * both {@code Workspace.leaderboardNotificationEnabled} and the {@code SlackConfig}
 * JSONB fields atomically). This controller exists only for the test-message
 * probe, which has no analogue in the workspace settings surface.
 */
@RestController
@RequestMapping("/api/v1/workspaces/{workspaceId}/connections/slack")
@RequireAtLeastWorkspaceAdmin
@ConditionalOnProperty(name = "hephaestus.integration.slack.enabled", havingValue = "true", matchIfMissing = false)
public class SlackConnectionAdminController {

    private static final Logger log = LoggerFactory.getLogger(SlackConnectionAdminController.class);

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
}
