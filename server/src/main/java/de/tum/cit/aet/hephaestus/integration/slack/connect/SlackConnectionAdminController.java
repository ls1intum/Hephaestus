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

/** Slack admin connectivity probe — test-message dispatch. */
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
            return ResponseEntity.status(statusForSlackError(e.slackError())).body(
                new SlackTestMessageResponse(false, channelId, e.slackError())
            );
        }
    }

    private static HttpStatus statusForSlackError(String slackError) {
        if (slackError == null) {
            return HttpStatus.BAD_GATEWAY;
        }
        return switch (slackError) {
            case
                "channel_not_found",
                "is_archived",
                "invalid_blocks",
                "invalid_arguments",
                "msg_too_long" -> (HttpStatus.BAD_REQUEST);
            case "not_in_channel", "missing_scope", "cannot_dm_bot" -> HttpStatus.FORBIDDEN;
            default -> HttpStatus.BAD_GATEWAY;
        };
    }
}
