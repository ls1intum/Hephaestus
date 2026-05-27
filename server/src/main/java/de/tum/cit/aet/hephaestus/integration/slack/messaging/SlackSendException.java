package de.tum.cit.aet.hephaestus.integration.slack.messaging;

/**
 * Raised by {@link SlackMessageService#sendForWorkspace} when {@code chat.postMessage}
 * fails — non-2xx, transport, or {@code ok=false}. Carries the Slack error code
 * ({@code channel_not_found}, {@code not_in_channel}, {@code rate_limited}, …) so
 * controllers can map it to a meaningful HTTP response without leaking tokens.
 */
public class SlackSendException extends RuntimeException {

    private final long workspaceId;
    private final String channelId;
    private final String slackError;

    public SlackSendException(long workspaceId, String channelId, String slackError) {
        super("slack send failed: workspaceId=" + workspaceId + " channelId=" + channelId + " error=" + slackError);
        this.workspaceId = workspaceId;
        this.channelId = channelId;
        this.slackError = slackError;
    }

    public SlackSendException(long workspaceId, String channelId, String slackError, Throwable cause) {
        super(
            "slack send failed: workspaceId=" + workspaceId + " channelId=" + channelId + " error=" + slackError,
            cause
        );
        this.workspaceId = workspaceId;
        this.channelId = channelId;
        this.slackError = slackError;
    }

    public long workspaceId() {
        return workspaceId;
    }

    public String channelId() {
        return channelId;
    }

    public String slackError() {
        return slackError;
    }
}
