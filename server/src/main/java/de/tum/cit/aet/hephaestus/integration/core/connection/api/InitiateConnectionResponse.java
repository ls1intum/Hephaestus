package de.tum.cit.aet.hephaestus.integration.core.connection.api;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.swagger.v3.oas.annotations.media.Schema;
import java.net.URI;

/**
 * Sealed response shape for {@code POST /api/v1/workspaces/{workspaceId}/connections}.
 *
 * <p>Two variants discriminated by the {@code type} property:
 * <ul>
 *   <li>{@link RedirectDTO} — for OAuth / App-install flows (GitHub, Slack).
 *       Carries the vendor URL to bounce the browser to plus the signed OAuth state.</li>
 *   <li>{@link LinkedDTO} — for inline-credential flows (GitLab PAT paste). Carries the
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
        @JsonSubTypes.Type(value = InitiateConnectionResponse.RedirectDTO.class, name = "redirect"),
        @JsonSubTypes.Type(value = InitiateConnectionResponse.LinkedDTO.class, name = "linked"),
    }
)
// Schema name carries the DTO suffix so the OpenAPI customizer (which keeps only DTO-suffixed
// schemas) retains it; the Java type stays suffix-free since arch rules reserve the DTO suffix
// for records / nested classes / dto packages, and this is a top-level sealed interface.
@Schema(name = "InitiateConnectionResponseDTO")
public sealed interface InitiateConnectionResponse
    permits InitiateConnectionResponse.RedirectDTO, InitiateConnectionResponse.LinkedDTO
{
    record RedirectDTO(URI vendorUrl, String state) implements InitiateConnectionResponse {}

    record LinkedDTO(Long connectionId) implements InitiateConnectionResponse {}
}
