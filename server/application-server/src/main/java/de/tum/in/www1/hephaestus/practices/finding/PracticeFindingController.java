package de.tum.in.www1.hephaestus.practices.finding;

import de.tum.in.www1.hephaestus.practices.finding.dto.ContributorPracticeSummaryDTO;
import de.tum.in.www1.hephaestus.practices.finding.dto.PracticeFindingDetailDTO;
import de.tum.in.www1.hephaestus.practices.finding.dto.PracticeFindingListDTO;
import de.tum.in.www1.hephaestus.practices.model.Verdict;
import de.tum.in.www1.hephaestus.workspace.context.WorkspaceContext;
import de.tum.in.www1.hephaestus.workspace.context.WorkspaceScopedController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Read-only REST API for practice findings.
 *
 * <p>All endpoints require workspace membership (enforced by {@link WorkspaceScopedController}).
 * List, summary, and detail endpoints are scoped to the authenticated contributor's own
 * findings. The pull-request endpoint returns findings for all contributors on that PR.
 */
@WorkspaceScopedController
@RequestMapping("/practices/findings")
@Tag(name = "Practice Findings", description = "Read-only access to practice evaluation findings")
@RequiredArgsConstructor
@Validated
public class PracticeFindingController {

    private final PracticeFindingService practiceFindingService;

    @GetMapping
    @Operation(
        summary = "List findings for current user",
        description = "Paginated findings for the authenticated contributor with optional filters"
    )
    @ApiResponse(responseCode = "200", description = "Paginated findings returned")
    @SecurityRequirements
    public ResponseEntity<Page<PracticeFindingListDTO>> listFindings(
        WorkspaceContext workspaceContext,
        @Parameter(description = "Filter by practice slug") @RequestParam(required = false) String practiceSlug,
        @Parameter(description = "Filter by verdict") @RequestParam(required = false) Verdict verdict,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        int safePage = Math.max(page, 0);
        int pageSize = Math.max(1, Math.min(size, 100));
        Pageable pageable = PageRequest.of(safePage, pageSize, Sort.by("detectedAt").descending());

        Page<PracticeFindingListDTO> findings = practiceFindingService
            .getFindings(workspaceContext.id(), practiceSlug, verdict, pageable)
            .map(PracticeFindingListDTO::from);
        return ResponseEntity.ok(findings);
    }

    @GetMapping("/summary")
    @Operation(
        summary = "Per-practice summary for current user",
        description = "Aggregated verdict counts per practice for dashboard cards"
    )
    @ApiResponse(
        responseCode = "200",
        description = "Practice summaries returned",
        content = @Content(array = @ArraySchema(schema = @Schema(implementation = ContributorPracticeSummaryDTO.class)))
    )
    @SecurityRequirements
    public ResponseEntity<List<ContributorPracticeSummaryDTO>> getSummary(WorkspaceContext workspaceContext) {
        List<ContributorPracticeSummaryDTO> summaries = practiceFindingService
            .getSummary(workspaceContext.id())
            .stream()
            .map(ContributorPracticeSummaryDTO::from)
            .toList();
        return ResponseEntity.ok(summaries);
    }

    @GetMapping("/{findingId}")
    @Operation(summary = "Get finding detail")
    @ApiResponse(
        responseCode = "200",
        description = "Finding detail returned",
        content = @Content(schema = @Schema(implementation = PracticeFindingDetailDTO.class))
    )
    @ApiResponse(
        responseCode = "404",
        description = "Finding not found or not owned by current user",
        content = @Content(schema = @Schema(hidden = true))
    )
    @SecurityRequirements
    public ResponseEntity<PracticeFindingDetailDTO> getFinding(
        WorkspaceContext workspaceContext,
        @PathVariable UUID findingId
    ) {
        var finding = practiceFindingService.getFinding(workspaceContext.id(), findingId);
        return ResponseEntity.ok(PracticeFindingDetailDTO.from(finding));
    }

    @GetMapping("/pull-request/{prId}")
    @Operation(
        summary = "List findings for a pull request",
        description = "All findings for a specific pull request within the workspace"
    )
    @ApiResponse(
        responseCode = "200",
        description = "PR findings returned",
        content = @Content(array = @ArraySchema(schema = @Schema(implementation = PracticeFindingListDTO.class)))
    )
    @SecurityRequirements
    public ResponseEntity<List<PracticeFindingListDTO>> getFindingsForPullRequest(
        WorkspaceContext workspaceContext,
        @PathVariable Long prId
    ) {
        List<PracticeFindingListDTO> findings = practiceFindingService
            .getFindingsForPullRequest(workspaceContext.id(), prId)
            .stream()
            .map(PracticeFindingListDTO::from)
            .toList();
        return ResponseEntity.ok(findings);
    }
}
