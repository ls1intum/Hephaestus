package de.tum.cit.aet.hephaestus.integration.outline.connect;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import org.jspecify.annotations.NonNull;

/**
 * Live state of the API token behind the workspace's Outline connection, probed against Outline
 * rather than read from our own database: whether Outline still accepts it, and — when the token may
 * list its own key — how Outline labels it, when it lapses and when it was last used.
 *
 * <p>Outline cannot rotate a key from a key: {@code apiKeys.create} and {@code apiKeys.delete} accept
 * only an interactive session, never a bearer token. Rotation is therefore a human act in Outline
 * followed by a re-connect here, and the value this endpoint adds is warning the admin before the
 * token lapses instead of after the mirror has gone quiet.
 */
@Schema(description = "Live state of the API token behind the workspace's Outline connection")
public record OutlineTokenStatusDTO(
    @NonNull
    @Schema(description = "Whether Outline still accepts the stored token. False means revoked, expired or rejected.")
    Boolean accepted,
    @Schema(
        description = "The token's name in Outline. Absent when the token cannot list its own key " +
            "(a scoped key, or one owned by a user who cannot see it) — sync is unaffected."
    )
    String name,
    @Schema(description = "Last four characters of the token, as Outline reports them") String last4,
    @Schema(description = "When the token lapses. Absent for a key created without an expiry.") Instant expiresAt,
    @Schema(description = "When Outline last saw the token used") Instant lastActiveAt
) {}
