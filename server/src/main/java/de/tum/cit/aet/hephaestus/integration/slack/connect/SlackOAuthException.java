package de.tum.cit.aet.hephaestus.integration.slack.connect;

/**
 * Raised by {@link SlackOAuthClient} when the {@code oauth.v2.access} call fails — HTTP
 * non-2xx, malformed body, or Slack-reported {@code ok=false}. Carries only the Slack
 * error string (or a short transport hint), never the credentials or the raw request.
 */
public class SlackOAuthException extends RuntimeException {

    public SlackOAuthException(String message) {
        super(message);
    }

    public SlackOAuthException(String message, Throwable cause) {
        super(message, cause);
    }
}
