package de.tum.cit.aet.hephaestus.integration.slack.messaging;

/** Raised by {@link SlackMessageService#sendForWorkspace}; carries the Slack error code. */
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
