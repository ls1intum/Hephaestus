package de.tum.cit.aet.hephaestus.integration.slack.channel;

/**
 * Signals that a requested Slack channel consent transition violates the state machine — e.g. pausing a channel
 * that was never activated, or trying to transition a {@code REVOKED} channel instead of registering it again. Mirrors
 * {@code WorkspaceLifecycleViolationException}: the {@code SlackChannelControllerAdvice} maps it to a
 * {@code 409 Conflict} {@code ProblemDetail}.
 */
public class SlackChannelConsentViolationException extends RuntimeException {

    public SlackChannelConsentViolationException(String message) {
        super(message);
    }
}
