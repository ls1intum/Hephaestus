package de.tum.cit.aet.hephaestus.integration.core.connection.api;

import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationState;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import org.jspecify.annotations.Nullable;

/**
 * Request to transition a Connection to a new admin-settable lifecycle state.
 *
 * <p>Admin-settable targets are {@code ACTIVE} (reactivate), {@code SUSPENDED} (suspend), and
 * {@code UNINSTALLED} (disconnect — additionally revokes the vendor-side token). {@code PENDING}
 * is internal to the OAuth handshake and is rejected. Illegal transitions (per the
 * {@code IntegrationState} state machine) surface as a {@code 400 ProblemDetail}.
 */
@Schema(description = "Request to update a connection's lifecycle status")
public record UpdateConnectionStatusRequestDTO(
    @NotNull(message = "state is required")
    @Schema(description = "Target lifecycle state. Admin-settable: ACTIVE, SUSPENDED, UNINSTALLED.")
    IntegrationState state,
    @Nullable @Schema(description = "Optional human-readable reason recorded on the audit trail") String reason
) {}
