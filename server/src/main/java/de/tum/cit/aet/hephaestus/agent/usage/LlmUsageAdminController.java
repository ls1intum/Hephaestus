package de.tum.cit.aet.hephaestus.agent.usage;

import de.tum.cit.aet.hephaestus.agent.usage.LlmUsageDTOs.AdminWorkspaceLlmUsageDTO;
import de.tum.cit.aet.hephaestus.agent.usage.LlmUsageDTOs.UpdateWorkspaceLlmBudgetRequestDTO;
import de.tum.cit.aet.hephaestus.core.Audited;
import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;
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
 * Instance-admin LLM cost governance (#1368): cross-workspace month rollup (spend totals only —
 * metadata, no tenant content) and the per-workspace monthly budget cap. The cap lives on the
 * instance-admin surface exclusively: it is the instance operator's backstop against a runaway
 * workspace, so workspace admins can see it but never raise it.
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

    @GetMapping("/llm-usage")
    @Operation(
        summary = "Per-workspace LLM spend rollup for one month (all workspaces)",
        operationId = "adminListLlmUsage"
    )
    public ResponseEntity<List<AdminWorkspaceLlmUsageDTO>> list(
        @RequestParam(required = false) @Pattern(
            regexp = "\\d{4}-(0[1-9]|1[0-2])",
            message = "month must be ISO yyyy-MM"
        ) @Nullable String month
    ) {
        YearMonth target = month != null ? YearMonth.parse(month) : YearMonth.now(ZoneOffset.UTC);
        return ResponseEntity.ok(llmUsageAdminService.getWorkspaceRollups(target));
    }

    @PutMapping("/workspaces/{workspaceId}/llm-budget")
    @Operation(
        summary = "Set or clear a workspace's monthly LLM budget cap",
        operationId = "adminUpdateWorkspaceLlmBudget"
    )
    @Audited("WORKSPACE_LLM_BUDGET")
    public ResponseEntity<Void> updateBudget(
        @PathVariable Long workspaceId,
        @Valid @RequestBody UpdateWorkspaceLlmBudgetRequestDTO request
    ) {
        llmUsageAdminService.updateBudget(workspaceId, request.monthlyLlmBudgetUsd());
        return ResponseEntity.noContent().build();
    }
}
