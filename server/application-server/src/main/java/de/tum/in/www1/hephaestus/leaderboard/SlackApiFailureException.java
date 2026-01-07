package de.tum.in.www1.hephaestus.leaderboard;

import java.io.Serial;

/**
 * Signals that a Slack API call failed.
 *
 * <p>This exception should be thrown when a Slack API operation fails, allowing callers
 * to distinguish between "no data available" and "API failure".
 */
public class SlackApiFailureException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public SlackApiFailureException(String message) {
        super(message);
    }

    public SlackApiFailureException(String message, Throwable cause) {
        super(message, cause);
    }
}
