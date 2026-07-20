package de.tum.cit.aet.hephaestus.agent.catalog;

import de.tum.cit.aet.hephaestus.core.Audited;
import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Instance-admin management of the instance-wide LLM governance settings (#1368): the egress
 * allowlist, the workspace-BYO-connection flag, and the default unpriced-usage policy. GLOBAL —
 * gated by {@code app_admin}, not workspace context.
 */
@RestController
@RequestMapping("/admin/llm/settings")
@Tag(name = "Admin LLM", description = "Instance-admin LLM connection and settings management")
@PreAuthorize("hasAuthority('app_admin')")
@WorkspaceAgnostic("Instance-admin LLM settings; authorized by app_admin, not workspace context")
@RequiredArgsConstructor
@Validated
public class InstanceLlmSettingsController {

    private final InstanceLlmSettingsService settingsService;

    @GetMapping
    @Operation(summary = "Get instance-wide LLM governance settings", operationId = "adminGetLlmSettings")
    public ResponseEntity<InstanceLlmSettingsDTO> get() {
        return ResponseEntity.ok(InstanceLlmSettingsDTO.from(settingsService.get()));
    }

    @PutMapping
    @Operation(summary = "Update instance-wide LLM governance settings", operationId = "adminUpdateLlmSettings")
    @Audited("auth_event LLM_SETTINGS_CHANGED")
    public ResponseEntity<InstanceLlmSettingsDTO> update(@Valid @RequestBody UpdateInstanceLlmSettingsRequest request) {
        return ResponseEntity.ok(InstanceLlmSettingsDTO.from(settingsService.update(request)));
    }
}
