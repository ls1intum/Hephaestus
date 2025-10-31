package de.tum.in.www1.hephaestus.integrations.posthog;

public class PosthogClientException extends RuntimeException {
    public PosthogClientException(String message) {
        super(message);
    }

    public PosthogClientException(String message, Throwable cause) {
        super(message, cause);
    }
}
