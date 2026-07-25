package de.tum.cit.aet.hephaestus.agent.usage;

import de.tum.cit.aet.hephaestus.agent.usage.LlmUsageDTOs.AdminLlmUsageReportDTO;
import de.tum.cit.aet.hephaestus.agent.usage.LlmUsageDTOs.UpdateLlmBudgetRequestDTO;
import de.tum.cit.aet.hephaestus.core.Audited;
import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import java.time.YearMonth;
import java.time.ZoneOffset;
import org.jspecify.annotations.Nullable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Instance-admin LLM cost governance (#1368): the cross-workspace month rollup (spend totals only —
 * metadata, no tenant content) and the per-workspace monthly cap on host-funded spend.
 *
 * <h2>Why this cap is not on the workspace's own surface</h2>
 *
 * <p>Its twin, {@code PUT /workspaces/{workspaceSlug}/llm/budget}, has the same path tail, the same
 * body and the same audit shape — but a different parent, and deliberately so. This one is the
 * operator's backstop against a tenant, so it must be settable for a workspace the operator is not a
 * member of; the workspace-scoped surface denies exactly that, because
 * {@code WorkspaceAccessService.hasRole} refuses an empty role set BEFORE it considers super-admin
 * elevation. Hosting this cap under {@code /workspaces/{slug}} would mean punching a hole in that
 * guard for one endpoint. The two paths are parallel in everything a client has to learn, and differ
 * only where the authority genuinely differs.
 */
@RestController
@RequestMapping("/admin")
@Tag(name = "Admin", description = "Instance-admin account management")
@PreAuthorize("hasAuthority('app_admin')")
@WorkspaceAgnostic("Instance-admin cross-tenant spend overview; authorized by app_admin, not workspace context")
@Validated
public class LlmUsageAdminController {

    private final LlmUsageAdminService llmUsageAdminService;

    public LlmUsageAdminController(LlmUsageAdminService llmUsageAdminService) {
        this.llmUsageAdminService = llmUsageAdminService;
    }

    @GetMapping("/llm/usage")
    @Operation(
        summary = "Per-workspace LLM spend rollup for one month (all workspaces)",
        operationId = "adminGetLlmUsageReport"
    )
    public ResponseEntity<AdminLlmUsageReportDTO> getReport(
        @RequestParam(required = false) @Pattern(
            regexp = "\\d{4}-(0[1-9]|1[0-2])",
            message = "month must be ISO yyyy-MM"
        ) @Nullable String month
    ) {
        YearMonth target = month != null ? YearMonth.parse(month) : YearMonth.now(ZoneOffset.UTC);
        return ResponseEntity.ok(llmUsageAdminService.getReport(target));
    }

    @PutMapping("/workspaces/{workspaceSlug}/llm/budget")
    @Operation(
        summary = "Set or clear a workspace's monthly cap on host-funded LLM spend",
        operationId = "adminUpdateWorkspaceLlmBudget"
    )
    @Audited("WORKSPACE_INSTANCE_LLM_BUDGET")
    public ResponseEntity<Void> updateBudget(
        @PathVariable String workspaceSlug,
        @Valid @RequestBody UpdateLlmBudgetRequestDTO request
    ) {
        llmUsageAdminService.updateBudget(workspaceSlug, request.monthlyBudgetUsd());
        return ResponseEntity.noContent().build();
    }
}
