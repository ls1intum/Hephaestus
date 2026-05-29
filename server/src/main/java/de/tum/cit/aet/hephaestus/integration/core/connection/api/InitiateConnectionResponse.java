package de.tum.cit.aet.hephaestus.integration.core.connection.api;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import java.net.URI;

/**
 * Sealed response shape for {@code POST /api/v1/workspaces/{workspaceId}/connections}.
 *
 * <p>Two variants discriminated by the {@code type} property:
 * <ul>
 *   <li>{@link Redirect} — for OAuth / App-install flows (GitHub, Slack).
 *       Carries the vendor URL to bounce the browser to plus the signed OAuth state.</li>
 *   <li>{@link Linked} — for inline-credential flows (GitLab PAT paste). Carries the
 *       newly-created {@code Connection} id; no further round-trip is needed.</li>
 * </ul>
 *
 * <p>The discriminator-on-property serialization keeps the wire format stable even if
 * we add more variants later (e.g. a {@code form} variant returning a structured field
 * schema for richer credential prompts).
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes(
    {
        @JsonSubTypes.Type(value = InitiateConnectionResponse.Redirect.class, name = "redirect"),
        @JsonSubTypes.Type(value = InitiateConnectionResponse.Linked.class, name = "linked"),
    }
)
public sealed interface InitiateConnectionResponse
    permits InitiateConnectionResponse.Redirect, InitiateConnectionResponse.Linked
{
    record Redirect(URI vendorUrl, String state) implements InitiateConnectionResponse {}

    record Linked(Long connectionId) implements InitiateConnectionResponse {}
}
