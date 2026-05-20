package de.tum.cit.aet.hephaestus.integrations.posthog;

public class PosthogClientException extends RuntimeException {

    public PosthogClientException(String message) {
        super(message);
    }

    public PosthogClientException(String message, Throwable cause) {
        super(message, cause);
    }
}
