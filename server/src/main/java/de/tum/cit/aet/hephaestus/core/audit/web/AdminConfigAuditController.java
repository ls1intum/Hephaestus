package de.tum.cit.aet.hephaestus.core.audit.web;

import de.tum.cit.aet.hephaestus.core.audit.spi.ConfigAuditEntryViewDTO;
import de.tum.cit.aet.hephaestus.core.audit.spi.ConfigAuditFilterParams;
import de.tum.cit.aet.hephaestus.core.audit.spi.ConfigAuditQuery;
import de.tum.cit.aet.hephaestus.core.runtime.ConditionalOnServerRole;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Instance-admin viewer over the config audit trail, spanning workspaces. Sits beside
 * {@code /admin/audit} (the auth trail); the workspace-scoped view of the same data is
 * {@code /workspaces/{slug}/config-audit}.
 */
@ConditionalOnServerRole
@RestController
@RequestMapping("/admin/config-audit")
@Tag(name = "Admin", description = "Instance-admin account management")
@PreAuthorize("hasAuthority('app_admin')")
@RequiredArgsConstructor
public class AdminConfigAuditController {

    private final ConfigAuditQuery configAuditQuery;

    @GetMapping
    @Operation(
        summary = "List admin configuration changes across workspaces (paged, newest first)",
        operationId = "adminListConfigAuditEvents"
    )
    public ResponseEntity<Page<ConfigAuditEntryViewDTO>> list(
        @RequestParam(required = false) @Nullable Long workspaceId,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "50") int size,
        @ParameterObject ConfigAuditFilterParams filter
    ) {
        return ResponseEntity.ok(
            configAuditQuery.listForAdmin(workspaceId, filter.toFilter(), ConfigAuditFilterParams.pageable(page, size))
        );
    }
}
