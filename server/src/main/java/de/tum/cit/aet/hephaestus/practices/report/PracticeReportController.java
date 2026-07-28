package de.tum.cit.aet.hephaestus.practices.report;

import de.tum.cit.aet.hephaestus.core.audit.spi.DataAccessAuditPort;
import de.tum.cit.aet.hephaestus.core.audit.spi.DataAccessResourceType;
import de.tum.cit.aet.hephaestus.core.exception.AccessForbiddenException;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.User;
import de.tum.cit.aet.hephaestus.practices.report.dto.AreaHealthDTO;
import de.tum.cit.aet.hephaestus.practices.report.dto.PracticeReportCardDTO;
import de.tum.cit.aet.hephaestus.practices.report.dto.PracticeReportSummaryDTO;
import de.tum.cit.aet.hephaestus.workspace.CurrentAccountUsers;
import de.tum.cit.aet.hephaestus.workspace.HealthVisibility;
import de.tum.cit.aet.hephaestus.workspace.authorization.RequireAtLeastWorkspaceAdmin;
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
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Practice <b>reports</b> — a developer's synthesised practice cards, the mentor roster over them, and the
 * anonymised workspace-health rollup.
 *
 * <p>One noun, {@code report}, shared by the self-view and the mentor drill-down and backed by one
 * derivation, so a developer and their mentor read the same cards. Nothing here ranks people (ADR 0028).
 *
 * <p>Three audiences, three rules: every developer reads their own report; the roster and drill-down name
 * individuals and are ADMIN/OWNER only; the health rollup names nobody and follows the workspace's
 * {@link HealthVisibility}. Reads that name someone also write a disclosure row.
 */
@WorkspaceScopedController
@RequestMapping("/practices")
@Tag(
    name = "Practice Reports",
    description = "Per-developer practice reports and the anonymised workspace health rollup"
)
@RequiredArgsConstructor
public class PracticeReportController {

    /** Default and cap mirror {@code WorkspaceMembershipController#listMembers}. */
    private static final int MAX_PAGE_SIZE = 100;
    private static final int DEFAULT_PAGE_SIZE = 50;

    private static final String SELF_REFUSAL =
        "Requires a workspace membership with a synced developer identity to view your own report";
    private static final String VIEWER_REFUSAL =
        "Cannot record the required access disclosure for this view (no workspace identity for the viewer)";

    private final PracticeReportService reportService;
    private final WorkspaceAccessService accessService;
    private final CurrentAccountUsers currentAccountUsers;
    private final DataAccessAuditPort dataAccessAudit;

    /**
     * The authenticated developer's own report: per-practice cards they can read — why the practice matters,
     * what good looks like, where they stand, the specific feedback to act on, and what they already do well.
     * The third feedback channel alongside in-context SCM notes and the conversational mentor; the same
     * findings reorganised by practice for self-paced reflection.
     *
     * <p>The subject is resolved through the account's workspace-scoped identity rather than the request's
     * login, so a publicly-readable workspace cannot serve one person's report to someone who merely shares
     * their login on another provider. No disclosure row: reading your own data discloses it to nobody.
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
        description = "Requires a workspace membership with a synced developer identity",
        content = @Content(schema = @Schema(implementation = ProblemDetail.class))
    )
    @SecurityRequirements
    public ResponseEntity<List<PracticeReportCardDTO>> getMyPracticeReport(WorkspaceContext workspaceContext) {
        Long selfUserId = requireWorkspaceActorId(workspaceContext.id(), SELF_REFUSAL);
        return ResponseEntity.ok(reportService.getDeveloperReport(workspaceContext.id(), selfUserId));
    }

    /**
     * The mentor roster: one summary per developer with activity in the window — their status on each practice
     * area and a needs-attention triage flag. ADMIN or OWNER only, because it names individuals.
     *
     * <p>Paginated like {@code WorkspaceMembershipController#listMembers} (0-indexed {@code page},
     * {@code size} default 50, capped at 100).
     */
    @GetMapping("/reports")
    @RequireAtLeastWorkspaceAdmin
    @Operation(
        summary = "List developer practice reports (admin/owner only)",
        description = "One summary per developer with activity in the window: per-area status (rolled up across " +
            "that area's practices, with a trend against the previous window) plus a needs-attention triage flag. " +
            "Sorted needs-attention-first then login, paginated (page/size, size default 50 capped at 100). Not a " +
            "scoreboard — no score, no rank."
    )
    @ApiResponse(
        responseCode = "200",
        description = "Report summaries returned",
        content = @Content(array = @ArraySchema(schema = @Schema(implementation = PracticeReportSummaryDTO.class)))
    )
    @ApiResponse(
        responseCode = "403",
        description = "Requires workspace ADMIN or OWNER",
        content = @Content(schema = @Schema(implementation = ProblemDetail.class))
    )
    @SecurityRequirements
    public ResponseEntity<List<PracticeReportSummaryDTO>> listPracticeReports(
        WorkspaceContext workspaceContext,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "" + DEFAULT_PAGE_SIZE) int size
    ) {
        Long viewerUserId = requireWorkspaceActorId(workspaceContext.id(), VIEWER_REFUSAL);
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, MAX_PAGE_SIZE));
        List<PracticeReportSummaryDTO> reports = reportService.listReports(workspaceContext.id(), pageable);
        dataAccessAudit.recordDisclosure(
            workspaceContext.id(),
            viewerUserId,
            null,
            DataAccessResourceType.PRACTICE_ROSTER
        );
        return ResponseEntity.ok(reports);
    }

    /**
     * A mentor's drill-down into one developer's report — the same cards that developer sees.
     *
     * <p>Writes an append-only disclosure row (actor → subject) after the read, and its failure propagates:
     * an unrecordable disclosure is refused rather than served unrecorded. That row is what lets the subject
     * be told who has seen their feedback (Art. 15(1)(c), served through the GDPR export).
     */
    @GetMapping("/reports/{userId}")
    @RequireAtLeastWorkspaceAdmin
    @Operation(
        summary = "Per-developer practice report drill-down (admin/owner only)",
        description = "The developer's own practice report cards, for a mentor. Records an append-only " +
            "disclosure row (actor to subject) so the workspace can answer who has seen someone's feedback."
    )
    @ApiResponse(
        responseCode = "200",
        description = "Report cards for the developer returned",
        content = @Content(array = @ArraySchema(schema = @Schema(implementation = PracticeReportCardDTO.class)))
    )
    @ApiResponse(
        responseCode = "403",
        description = "Requires workspace ADMIN or OWNER",
        content = @Content(schema = @Schema(implementation = ProblemDetail.class))
    )
    @ApiResponse(
        responseCode = "404",
        description = "No report subject with activity in the window exists for this user in the workspace",
        content = @Content(schema = @Schema(implementation = ProblemDetail.class))
    )
    @SecurityRequirements
    public ResponseEntity<List<PracticeReportCardDTO>> getDeveloperPracticeReport(
        WorkspaceContext workspaceContext,
        @PathVariable Long userId
    ) {
        Long viewerUserId = requireWorkspaceActorId(workspaceContext.id(), VIEWER_REFUSAL);
        reportService.requireVisibleSubject(workspaceContext.id(), userId);
        List<PracticeReportCardDTO> cards = reportService.getDeveloperReport(workspaceContext.id(), userId);
        dataAccessAudit.recordDisclosure(
            workspaceContext.id(),
            viewerUserId,
            userId,
            DataAccessResourceType.PRACTICE_REPORT
        );
        return ResponseEntity.ok(cards);
    }

    /**
     * Workspace health per practice area — anonymised, never per-person. Admins and owners always; ordinary
     * members only when the workspace's {@link HealthVisibility} is {@link HealthVisibility#EVERYONE}.
     */
    @GetMapping("/health")
    @Operation(
        summary = "Workspace practice health",
        description = "Per practice area, how many developers stand at each status, anonymised: counts are " +
            "withheld when the group is too small, or when a status bucket is small or large enough to identify " +
            "who is in it. An area with no active developers is reported as no-data rather than suppressed — " +
            "there is nobody to identify. Admins/owners always; members only when health visibility is EVERYONE."
    )
    @ApiResponse(
        responseCode = "200",
        description = "Workspace health cards returned",
        content = @Content(array = @ArraySchema(schema = @Schema(implementation = AreaHealthDTO.class)))
    )
    @ApiResponse(
        responseCode = "403",
        description = "Not permitted for this workspace's health visibility",
        content = @Content(schema = @Schema(implementation = ProblemDetail.class))
    )
    @SecurityRequirements
    public ResponseEntity<List<AreaHealthDTO>> listPracticeHealth(WorkspaceContext workspaceContext) {
        boolean isAdmin = accessService.isAdmin();
        if (
            !isAdmin && !(accessService.isMember() && workspaceContext.healthVisibility() == HealthVisibility.EVERYONE)
        ) {
            throw new AccessForbiddenException("Not permitted to view the practice health of this workspace");
        }
        return ResponseEntity.ok(reportService.getWorkspaceHealth(workspaceContext.id(), !isAdmin));
    }

    /**
     * The caller's SCM actor in this workspace, or 403.
     *
     * <p>Membership is the requirement, not merely a resolvable login. On the self-view a non-member has no
     * report to read, and answering with an empty list would misreport that as "no feedback yet". On the
     * admin surfaces there would be nobody to name in the disclosure record — an elevated instance admin who
     * is not a member lands here intentionally, because reading a named report is a workspace-level act.
     */
    private Long requireWorkspaceActorId(Long workspaceId, String refusal) {
        return currentAccountUsers
            .resolveMemberOf(workspaceId)
            .map(User::getId)
            .orElseThrow(() -> new AccessForbiddenException(refusal));
    }
}
