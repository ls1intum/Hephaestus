package de.tum.cit.aet.hephaestus.core.settings;

import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import de.tum.cit.aet.hephaestus.core.auth.web.CurrentAccount;
import de.tum.cit.aet.hephaestus.core.runtime.ConditionalOnServerRole;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Instance-admin settings API, guarded by the namespaced {@code app_admin} authority. Thin adapter
 * over {@link InstanceSettingsService}. The silent-mode transition is a {@code PUT} (idempotent
 * full replacement of the silent-mode subresource) so an emergency engage can be retried blindly.
 */
@ConditionalOnServerRole
@RestController
@WorkspaceAgnostic("Instance-wide operator settings — deliberately cross-tenant, app_admin only")
@RequestMapping("/admin/settings")
@Tag(name = "Admin", description = "Instance-admin settings")
@PreAuthorize("hasAuthority('app_admin')")
public class InstanceSettingsAdminController {

    private final InstanceSettingsService instanceSettingsService;

    InstanceSettingsAdminController(InstanceSettingsService instanceSettingsService) {
        this.instanceSettingsService = instanceSettingsService;
    }

    public record InstanceSettingsDTO(
        @NonNull Boolean silentModeEngaged,
        @Nullable String silentModeReason,
        @Nullable Instant silentModeChangedAt,
        @Nullable String silentModeChangedBy
    ) {
        static InstanceSettingsDTO from(InstanceSettings settings) {
            return new InstanceSettingsDTO(
                settings.isSilentModeEngaged(),
                settings.getSilentModeReason(),
                settings.getSilentModeChangedAt(),
                settings.getSilentModeChangedBy()
            );
        }
    }

    public record UpdateSilentModeRequestDTO(
        // Boxed @NotNull on purpose: a primitive would let Jackson default a missing field to false,
        // silently releasing the brake on a malformed request. (Kept out of the record Javadoc so it
        // doesn't leak into the generated OpenAPI schema description.)
        @NonNull @NotNull Boolean engaged,
        @Nullable @Size(max = 500) String reason
    ) {}

    @GetMapping
    @Operation(summary = "Get instance settings", operationId = "adminGetInstanceSettings")
    public ResponseEntity<InstanceSettingsDTO> get() {
        return ResponseEntity.ok(InstanceSettingsDTO.from(instanceSettingsService.get()));
    }

    @PutMapping("/silent-mode")
    @Operation(summary = "Engage or release the instance-wide silent mode", operationId = "adminUpdateSilentMode")
    public ResponseEntity<InstanceSettingsDTO> updateSilentMode(@Valid @RequestBody UpdateSilentModeRequestDTO body) {
        InstanceSettings updated = instanceSettingsService.updateSilentMode(
            body.engaged(),
            body.reason(),
            CurrentAccount.preferredUsernameOrNull()
        );
        return ResponseEntity.ok(InstanceSettingsDTO.from(updated));
    }
}
