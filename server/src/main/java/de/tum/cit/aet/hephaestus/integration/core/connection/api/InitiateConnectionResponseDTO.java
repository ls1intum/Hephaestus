package de.tum.cit.aet.hephaestus.integration.core.connection.api;

import java.net.URI;
import org.jspecify.annotations.Nullable;

/**
 * Flat response for {@code POST /workspaces/{workspaceSlug}/connections}.
 *
 * <p>Two outcomes, distinguished by {@link #type}:
 * <ul>
 *   <li>{@code REDIRECT} — OAuth / App-install flows (GitHub, Slack). {@link #vendorUrl} is the
 *       URL to bounce the browser to; {@link #state} is the signed OAuth state.</li>
 *   <li>{@code LINKED} — inline-credential flows (GitLab PAT). {@link #connectionId} is the
 *       newly-created Connection; no further round-trip is needed.</li>
 * </ul>
 *
 * <p>Deliberately a flat record (nullable per-variant fields) rather than a sealed
 * {@code oneOf} hierarchy: a discriminated union serialises to OpenAPI {@code oneOf +
 * discriminator}, which the webapp's hey-api react-query transformer mishandles. A flat shape
 * round-trips cleanly through code generation, so the webapp consumes the generated client
 * instead of a hand-rolled fetch wrapper.
 */
public record InitiateConnectionResponseDTO(
    Type type,
    @Nullable URI vendorUrl,
    @Nullable String state,
    @Nullable Long connectionId
) {
    public enum Type {
        REDIRECT,
        LINKED,
    }

    public static InitiateConnectionResponseDTO redirect(URI vendorUrl, String state) {
        return new InitiateConnectionResponseDTO(Type.REDIRECT, vendorUrl, state, null);
    }

    public static InitiateConnectionResponseDTO linked(Long connectionId) {
        return new InitiateConnectionResponseDTO(Type.LINKED, null, null, connectionId);
    }
}
