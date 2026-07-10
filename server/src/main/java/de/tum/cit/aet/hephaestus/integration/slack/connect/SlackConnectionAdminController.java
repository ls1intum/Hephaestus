package de.tum.cit.aet.hephaestus.integration.slack.connect;

import de.tum.cit.aet.hephaestus.core.runtime.ConditionalOnServerRole;
import de.tum.cit.aet.hephaestus.integration.slack.messaging.SlackMessageService;
import de.tum.cit.aet.hephaestus.integration.slack.messaging.SlackSendException;
import de.tum.cit.aet.hephaestus.workspace.authorization.RequireAtLeastWorkspaceAdmin;
import de.tum.cit.aet.hephaestus.workspace.context.WorkspaceContext;
import de.tum.cit.aet.hephaestus.workspace.context.WorkspaceScopedController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

/** Slack admin connectivity probe — test-message dispatch. */
@WorkspaceScopedController
@RequestMapping("/connections/slack")
@RequireAtLeastWorkspaceAdmin
@ConditionalOnServerRole
@ConditionalOnProperty(name = "hephaestus.integration.slack.enabled", havingValue = "true", matchIfMissing = false)
@Tag(name = "Connections", description = "Workspace integration connection management")
public class SlackConnectionAdminController {

    private static final Logger log = LoggerFactory.getLogger(SlackConnectionAdminController.class);

    private final SlackMessageService slackMessageService;

    public SlackConnectionAdminController(SlackMessageService slackMessageService) {
        this.slackMessageService = slackMessageService;
    }

    /**
     * Probe the Slack connection by posting a test message. This is a <em>probe</em>: every outcome
     * — success, a Slack-side rejection (e.g. {@code not_in_channel}), or a missing channel — is a
     * 200 result carrying {@code ok}/{@code slackError}, never an HTTP error. That lets the admin UI
     * test a typed-but-not-yet-saved channel and render the Slack error inline without conflating it
     * with a transport failure.
     *
     * @param body the channel to probe; when blank the probe reports {@code no_channel_configured}
     *     (nothing writes a persisted default channel since the digest removal).
     */
    @PostMapping("/test-message")
    @Operation(operationId = "sendSlackTestMessage", summary = "Post a test message to verify the Slack connection")
    public SlackTestMessageResponseDTO sendTestMessage(
        WorkspaceContext workspace,
        @RequestBody(required = false) @Nullable SlackTestMessageRequestDTO body
    ) {
        long workspaceId = workspace.id();
        String override = body == null ? null : body.channelId();
        String channelId = (override != null && !override.isBlank()) ? override.trim() : null;

        if (channelId == null || channelId.isBlank()) {
            return new SlackTestMessageResponseDTO(false, null, "no_channel_configured");
        }

        try {
            slackMessageService.sendForWorkspace(
                workspaceId,
                channelId,
                List.of(),
                "Hephaestus test message — your Slack integration is wired up."
            );
            return new SlackTestMessageResponseDTO(true, channelId, null);
        } catch (SlackSendException e) {
            log.warn(
                "Slack test message failed: workspaceId={}, channelId={}, error={}",
                workspaceId,
                channelId,
                e.slackError()
            );
            return new SlackTestMessageResponseDTO(false, channelId, e.slackError());
        }
    }
}
