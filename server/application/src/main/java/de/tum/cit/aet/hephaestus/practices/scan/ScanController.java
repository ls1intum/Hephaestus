package de.tum.cit.aet.hephaestus.practices.scan;

import de.tum.cit.aet.hephaestus.core.runtime.ConditionalOnServerRole;
import de.tum.cit.aet.hephaestus.core.security.SecurityUtils;
import de.tum.cit.aet.hephaestus.practices.scan.dto.CancelScanRequestDTO;
import de.tum.cit.aet.hephaestus.practices.scan.dto.ScanDetailDTO;
import de.tum.cit.aet.hephaestus.practices.scan.dto.ScanReportDTO;
import de.tum.cit.aet.hephaestus.practices.scan.dto.ScanReportItemsPageDTO;
import de.tum.cit.aet.hephaestus.practices.scan.dto.ScanReportProblemsPageDTO;
import de.tum.cit.aet.hephaestus.practices.scan.dto.ScanSummaryDTO;
import de.tum.cit.aet.hephaestus.practices.scan.dto.StartScanRequestDTO;
import de.tum.cit.aet.hephaestus.practices.spi.ProblemBaselineState;
import de.tum.cit.aet.hephaestus.practices.spi.ResultOutcome;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewStatus;
import de.tum.cit.aet.hephaestus.practices.spi.Severity;
import de.tum.cit.aet.hephaestus.workspace.authorization.RequireAtLeastWorkspaceAdmin;
import de.tum.cit.aet.hephaestus.workspace.context.WorkspaceContext;
import de.tum.cit.aet.hephaestus.workspace.context.WorkspaceScopedController;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.net.URI;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.web.PagedModel;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@WorkspaceScopedController
@ConditionalOnServerRole
@RequireAtLeastWorkspaceAdmin
@RequiredArgsConstructor
@Validated
@RequestMapping("/practices/scans")
public class ScanController {
    private final ScanQueryService query;

    @GetMapping
    @Operation(operationId = "listScans", summary = "List repository scans")
    public PagedModel<ScanSummaryDTO> listScans(
            WorkspaceContext context,
            @RequestParam(required = false) @Nullable String repositoryItemId,
            @RequestParam(required = false) @Nullable ReviewStatus status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "25") @Min(1) @Max(100) int size) {
        return new PagedModel<>(query.list(
                context.id(),
                repositoryItemId == null ? null : Long.parseLong(repositoryItemId),
                status,
                PageRequest.of(page, size)));
    }

    @PostMapping
    @Operation(operationId = "startScan", summary = "Start a repository scan")
    public ResponseEntity<ScanSummaryDTO> startScan(
            WorkspaceContext context,
            @RequestHeader("Idempotency-Key") UUID key,
            @Valid @RequestBody StartScanRequestDTO request) {
        var scan = query.start(context.id(), SecurityUtils.getCurrentAccountId().orElseThrow(), key, request);
        return ResponseEntity.accepted()
                .location(URI.create("/workspaces/" + context.slug() + "/practices/scans/" + scan.id()))
                .body(scan);
    }

    @GetMapping("/{scanId}")
    @Operation(operationId = "getScan", summary = "Read a repository scan")
    public ScanDetailDTO getScan(WorkspaceContext context, @PathVariable UUID scanId) {
        return query.detail(context.id(), scanId);
    }

    @PatchMapping("/{scanId}/status")
    @Operation(operationId = "cancelScan", summary = "Cancel a repository scan")
    public ResponseEntity<ScanDetailDTO> cancelScan(
            WorkspaceContext context, @PathVariable UUID scanId, @Valid @RequestBody CancelScanRequestDTO request) {
        var detail = query.cancel(context.id(), scanId);
        return ResponseEntity.status(
                        detail.status() == ReviewStatus.QUEUED || detail.status() == ReviewStatus.RUNNING ? 202 : 200)
                .body(detail);
    }

    @GetMapping("/{scanId}/report")
    @Operation(operationId = "getScanReport", summary = "Read a scan report")
    public ScanReportDTO getScanReport(WorkspaceContext context, @PathVariable UUID scanId) {
        return query.report(context.id(), scanId);
    }

    @GetMapping("/{scanId}/report/items")
    @Operation(operationId = "listScanReportItems", summary = "List the items in a scan report")
    public ScanReportItemsPageDTO listScanReportItems(
            WorkspaceContext context,
            @PathVariable UUID scanId,
            @RequestParam(required = false) @Nullable String cursor,
            @RequestParam(defaultValue = "50") @Min(1) @Max(200) int size,
            @RequestParam(required = false) @Nullable String practiceSlug,
            @RequestParam(required = false) @Nullable ResultOutcome outcome,
            @RequestParam(required = false) @Nullable String status,
            @RequestParam(required = false) @Nullable Boolean reused,
            @RequestParam(required = false) @Nullable String path) {
        return query.items(
                context.id(),
                scanId,
                cursor,
                size,
                new ScanQueryService.ItemFilter(practiceSlug, outcome, status, reused, path));
    }

    @GetMapping("/{scanId}/report/problems")
    @Operation(operationId = "listScanReportProblems", summary = "List the problems in a scan report")
    public ScanReportProblemsPageDTO listScanReportProblems(
            WorkspaceContext context,
            @PathVariable UUID scanId,
            @RequestParam(required = false) @Nullable String cursor,
            @RequestParam(defaultValue = "50") @Min(1) @Max(200) int size,
            @RequestParam(required = false) @Nullable String practiceSlug,
            @RequestParam(required = false) @Nullable Severity severity,
            @RequestParam(required = false) @Nullable String path,
            @RequestParam(required = false) @Nullable ProblemBaselineState baselineState,
            @RequestParam(defaultValue = "true") boolean openOnly) {
        return query.problems(
                context.id(),
                scanId,
                cursor,
                size,
                new ScanQueryService.ProblemFilter(practiceSlug, severity, path, baselineState, openOnly));
    }

    @GetMapping(value = "/{scanId}/report/sarif", produces = "application/sarif+json")
    @Operation(operationId = "exportScanSarif", summary = "Export a scan report as SARIF")
    public ResponseEntity<byte[]> exportScanSarif(WorkspaceContext context, @PathVariable UUID scanId) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/sarif+json"))
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"hephaestus-scan-" + scanId + ".sarif\"")
                .body(query.sarif(context.id(), scanId));
    }
}
