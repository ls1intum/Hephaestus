package de.tum.cit.aet.hephaestus.agent.catalog;

import de.tum.cit.aet.hephaestus.core.AuditExempt;
import de.tum.cit.aet.hephaestus.core.Audited;
import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import de.tum.cit.aet.hephaestus.core.runtime.ConditionalOnServerRole;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * Instance-admin management of the LLM provider connection catalog (#1368): CRUD plus a "test &amp;
 * fetch models" probe. GLOBAL — gated by {@code app_admin}, not workspace context.
 */
@RestController
@RequestMapping("/admin/llm/connections")
@Tag(name = "Admin LLM", description = "Instance-admin LLM connection and settings management")
@PreAuthorize("hasAuthority('app_admin')")
@WorkspaceAgnostic("Instance-admin LLM connection catalog; authorized by app_admin, not workspace context")
@ConditionalOnServerRole
@RequiredArgsConstructor
@Validated
public class LlmConnectionAdminController {

    private final LlmConnectionService connectionService;
    private final LlmConnectionProbeService probeService;

    @GetMapping
    @Operation(summary = "List LLM connections", operationId = "adminListLlmConnections")
    public ResponseEntity<List<LlmConnectionDTO>> list() {
        return ResponseEntity.ok(connectionService.list().stream().map(LlmConnectionDTO::from).toList());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get an LLM connection", operationId = "adminGetLlmConnection")
    public ResponseEntity<LlmConnectionDTO> get(@PathVariable Long id) {
        return ResponseEntity.ok(LlmConnectionDTO.from(connectionService.get(id)));
    }

    @PostMapping
    @Operation(summary = "Create an LLM connection", operationId = "adminCreateLlmConnection")
    @Audited("auth_event LLM_CONNECTION_CREATED")
    public ResponseEntity<LlmConnectionDTO> create(@Valid @RequestBody CreateLlmConnectionRequest request) {
        LlmConnection created = connectionService.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
            .path("/{id}")
            .buildAndExpand(created.getId())
            .toUri();
        return ResponseEntity.created(location).body(LlmConnectionDTO.from(created));
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Update an LLM connection", operationId = "adminUpdateLlmConnection")
    @Audited("auth_event LLM_CONNECTION_UPDATED")
    public ResponseEntity<LlmConnectionDTO> update(
        @PathVariable Long id,
        @Valid @RequestBody UpdateLlmConnectionRequest request
    ) {
        return ResponseEntity.ok(LlmConnectionDTO.from(connectionService.update(id, request)));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete an LLM connection", operationId = "adminDeleteLlmConnection")
    @Audited("auth_event LLM_CONNECTION_DELETED")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        connectionService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/probe")
    @Operation(summary = "Test a stored connection and fetch its models", operationId = "adminProbeLlmConnection")
    @AuditExempt(reason = "tests a stored credential; stores no configuration")
    public ResponseEntity<LlmProbeResult> probe(@PathVariable Long id) {
        return ResponseEntity.ok(probeService.probeStored(id));
    }

    @PostMapping("/probe")
    @Operation(summary = "Test a draft connection and fetch its models", operationId = "adminProbeLlmConnectionDraft")
    @AuditExempt(reason = "tests a draft connection before it is saved; stores no configuration")
    public ResponseEntity<LlmProbeResult> probeDraft(@Valid @RequestBody ProbeLlmConnectionRequest request) {
        return ResponseEntity.ok(probeService.probeDraft(request));
    }
}
