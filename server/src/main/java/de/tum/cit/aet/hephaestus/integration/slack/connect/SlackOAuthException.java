package de.tum.cit.aet.hephaestus.integration.slack.connect;

public class SlackOAuthException extends RuntimeException {

    public SlackOAuthException(String message) {
        super(message);
    }

    public SlackOAuthException(String message, Throwable cause) {
        super(message, cause);
    }
}
