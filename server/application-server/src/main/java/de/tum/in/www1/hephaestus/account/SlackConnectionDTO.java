package de.tum.in.www1.hephaestus.account;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * DTO representing the user's Slack connection status.
 *
 * @param slackUserId  The Slack User ID if connected, null otherwise
 * @param connected    Whether the user has linked their Slack account
 * @param slackEnabled Whether Slack integration is enabled for this instance
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record SlackConnectionDTO(String slackUserId, boolean connected, boolean slackEnabled, String linkUrl) {
    /** Creates a connected status with the user's Slack ID. */
    public static SlackConnectionDTO connected(String slackUserId, boolean slackEnabled, String linkUrl) {
        return new SlackConnectionDTO(slackUserId, true, slackEnabled, linkUrl);
    }

    /** Creates a disconnected status. */
    public static SlackConnectionDTO disconnected(boolean slackEnabled, String linkUrl) {
        return new SlackConnectionDTO(null, false, slackEnabled, linkUrl);
    }
}
