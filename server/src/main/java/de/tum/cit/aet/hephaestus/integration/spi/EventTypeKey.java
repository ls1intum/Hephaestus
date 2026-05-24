package de.tum.cit.aet.hephaestus.integration.spi;

import org.springframework.lang.NonNull;

/**
 * Two-axis key for the unified {@code IntegrationMessageHandlerRegistry}.
 *
 * <p>The {@code eventType} string may carry domain prefixes for kinds that need them
 * (e.g., {@code "repository.issues"}, {@code "organization.repositories"},
 * {@code "installation.created"} for GitHub). One key → at most one handler.
 */
public record EventTypeKey(@NonNull IntegrationKind kind, @NonNull String eventType) {
    public EventTypeKey {
        if (kind == null) throw new IllegalArgumentException("kind must not be null");
        if (eventType == null || eventType.isBlank()) {
            throw new IllegalArgumentException("eventType must not be blank");
        }
    }
}
