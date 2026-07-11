package de.tum.cit.aet.hephaestus.practices.report;

import de.tum.cit.aet.hephaestus.core.audit.DataAccessAuditWriter;
import de.tum.cit.aet.hephaestus.core.exception.AccessForbiddenException;
import de.tum.cit.aet.hephaestus.core.exception.ProblemDetailSchema;
import de.tum.cit.aet.hephaestus.practices.observation.ObservationService;
import de.tum.cit.aet.hephaestus.practices.report.dto.CohortPracticeStatusDTO;
import de.tum.cit.aet.hephaestus.practices.report.dto.PracticeReportCardDTO;
import de.tum.cit.aet.hephaestus.practices.report.dto.PracticeReportSummaryDTO;
import de.tum.cit.aet.hephaestus.workspace.CohortVisibility;
import de.tum.cit.aet.hephaestus.workspace.authorization.WorkspaceAccessService;
import de.tum.cit.aet.hephaestus.workspace.context.WorkspaceContext;
import de.tum.cit.aet.hephaestus.workspace.context.WorkspaceScopedController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Practice <b>reports</b> — per-developer synthesised cards plus the anonymised cohort rollup. One noun
 * ({@code report}) shared by the developer's self-view ({@code /reports/me}) and the mentor drill-down
 * ({@code /reports/{userId}}), backed by one derivation. Access is enforced server-side per endpoint (see
 * each method) and a forbidden caller receives a 403 {@code ProblemDetail} via {@link AccessForbiddenException}.
 */
@WorkspaceScopedController
@RequestMapping("/practices")
@Tag(name = "Practice Reports", description = "Per-developer practice reports and the anonymised cohort rollup")
@RequiredArgsConstructor
public class PracticeReportController {

    private final PracticeReportService reportService;
    private final ObservationService observationService;
    private final WorkspaceAccessService accessService;
    private final DataAccessAuditWriter dataAccessAuditWriter;

    /**
     * Report roster: one summary per developer-with-activity — their per-practice status and a
     * needs-attention triage flag. ADMIN or OWNER only (it names individuals); members receive 403.
     */
    @GetMapping("/reports")
    @Operation(
        summary = "List developer practice reports (admin/owner only)",
        description = "One summary per developer with activity in the window: per-practice status + a " +
            "needs-attention triage flag. Sorted needs-attention-first then login. Not a scoreboard — no score/rank."
    )
    @ApiResponse(
        responseCode = "200",
        description = "Report summaries returned",
        content = @Content(array = @ArraySchema(schema = @Schema(implementation = PracticeReportSummaryDTO.class)))
    )
    @ApiResponse(
        responseCode = "403",
        description = "Requires workspace ADMIN or OWNER",
        content = @Content(schema = @Schema(implementation = ProblemDetailSchema.class))
    )
    @SecurityRequirements
    public ResponseEntity<List<PracticeReportSummaryDTO>> listPracticeReports(WorkspaceContext workspaceContext) {
        requireAdmin();
        Long viewerUserId = reportService.requireAuditableCurrentUserId();
        // Audit write-after-read (matching the drill-down): the writer runs in its own transaction, so
        // writing first would leave a committed disclosure row even when the read itself fails.
        List<PracticeReportSummaryDTO> reports = reportService.listReports(workspaceContext.id());
        dataAccessAuditWriter.recordRosterView(workspaceContext.id(), viewerUserId);
        return ResponseEntity.ok(reports);
    }

    /**
     * The authenticated developer's own report: per-practice cards they can READ — why the practice matters,
     * what good looks like, where they stand, the specific feedback to act on, and what they already do well.
     * The third feedback channel alongside in-context SCM notes and the conversational mentor; the same
     * findings reorganised by practice for self-paced reflection.
     *
     * <p>Deliberately requires no workspace membership: the subject is always the CALLER, so on a
     * publicly viewable workspace a past contributor who never joined can still read their own
     * reflection — self-view-only exposure, never anyone else's data.
     */
    @GetMapping("/reports/me")
    @Operation(
        summary = "The current developer's own practice report",
        description = "Per-practice cards a developer can READ — why the practice matters, what good looks like, " +
            "where they stand, the specific feedback to act on, and what they already do well. The third feedback " +
            "channel alongside in-context SCM notes and the conversational mentor; the same findings reorganised by " +
            "practice for self-paced reflection."
    )
    @ApiResponse(
        responseCode = "200",
        description = "Per-practice report cards returned",
        content = @Content(array = @ArraySchema(schema = @Schema(implementation = PracticeReportCardDTO.class)))
    )
    @ApiResponse(
        responseCode = "403",
        description = "Requires a synced developer identity in this workspace",
        content = @Content(schema = @Schema(implementation = ProblemDetailSchema.class))
    )
    @SecurityRequirements
    public ResponseEntity<List<PracticeReportCardDTO>> getMyPracticeReport(WorkspaceContext workspaceContext) {
        reportService.requireAuditableCurrentUserId();
        return ResponseEntity.ok(observationService.getPracticeReport(workspaceContext.id()));
    }

    /**
     * Per-developer report drill-down: that developer's own practice cards, for a mentor. ADMIN or OWNER only
     * (it names an individual). Serving this records an append-only transparency audit row (actor → subject) so
     * the disclosure remains a compliance record, even though there is no subject-facing view of it.
     */
    @GetMapping("/reports/{userId}")
    @Operation(
        summary = "Per-developer practice report drill-down (admin/owner only)",
        description = "The developer's own practice report cards, for a mentor. Records an append-only " +
            "transparency audit row (actor → subject) for compliance."
    )
    @ApiResponse(
        responseCode = "200",
        description = "Report cards for the developer returned",
        content = @Content(array = @ArraySchema(schema = @Schema(implementation = PracticeReportCardDTO.class)))
    )
    @ApiResponse(
        responseCode = "403",
        description = "Requires workspace ADMIN or OWNER",
        content = @Content(schema = @Schema(implementation = ProblemDetailSchema.class))
    )
    @ApiResponse(
        responseCode = "404",
        description = "No current-cycle report subject exists for this user in the workspace",
        content = @Content(schema = @Schema(implementation = ProblemDetailSchema.class))
    )
    @SecurityRequirements
    public ResponseEntity<List<PracticeReportCardDTO>> getDeveloperPracticeReport(
        WorkspaceContext workspaceContext,
        @PathVariable Long userId
    ) {
        requireAdmin();
        Long viewerUserId = reportService.requireAuditableCurrentUserId();
        List<PracticeReportCardDTO> cards = reportService.getDeveloperReport(workspaceContext.id(), userId);
        dataAccessAuditWriter.recordReportView(workspaceContext.id(), viewerUserId, userId);
        return ResponseEntity.ok(cards);
    }

    /**
     * Cohort practice status per reviewing practice (k-anonymised, never per-person). Visibility follows the
     * workspace's {@link CohortVisibility}: admins/owners always; regular members only when it is
     * {@link CohortVisibility#EVERYONE}; otherwise 403.
     */
    @GetMapping("/cohort")
    @Operation(
        summary = "Cohort practice status",
        description = "Per reviewing practice, how many developers stand at each status (k-anonymised, K=5 with small-bucket suppression). " +
            "Admins/owners always; members only when the workspace cohort visibility is EVERYONE."
    )
    @ApiResponse(
        responseCode = "200",
        description = "Cohort status cards returned",
        content = @Content(array = @ArraySchema(schema = @Schema(implementation = CohortPracticeStatusDTO.class)))
    )
    @ApiResponse(
        responseCode = "403",
        description = "Not permitted for this cohort visibility",
        content = @Content(schema = @Schema(implementation = ProblemDetailSchema.class))
    )
    @SecurityRequirements
    public ResponseEntity<List<CohortPracticeStatusDTO>> getCohortPracticeStatus(WorkspaceContext workspaceContext) {
        if (!canViewCohort(workspaceContext)) {
            throw new AccessForbiddenException("Not permitted to view the cohort status for this workspace");
        }
        return ResponseEntity.ok(reportService.getCohortStatus(workspaceContext.id()));
    }

    /** ADMIN or OWNER (super-admins with membership are elevated to ADMIN by the access service). */
    private void requireAdmin() {
        if (!accessService.isAdmin()) {
            throw new AccessForbiddenException("Workspace ADMIN or OWNER is required to view this resource");
        }
    }

    /**
     * Cohort visibility: admins/owners always; regular members only when the workspace's cohort visibility is
     * {@link CohortVisibility#EVERYONE}. Non-members are always denied.
     */
    private boolean canViewCohort(WorkspaceContext workspaceContext) {
        if (accessService.isAdmin()) {
            return true;
        }
        if (!workspaceContext.hasMembership()) {
            return false;
        }
        return workspaceContext.cohortVisibility() == CohortVisibility.EVERYONE;
    }
}
