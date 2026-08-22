package de.tum.cit.aet.hephaestus.practices;

import de.tum.cit.aet.hephaestus.core.AuditExempt;
import de.tum.cit.aet.hephaestus.core.AuditLedger;
import de.tum.cit.aet.hephaestus.core.Audited;
import de.tum.cit.aet.hephaestus.practices.dto.CreatePracticeAreaRequestDTO;
import de.tum.cit.aet.hephaestus.practices.dto.PracticeAreaDTO;
import de.tum.cit.aet.hephaestus.practices.dto.PracticeAreaStatusDTO;
import de.tum.cit.aet.hephaestus.practices.dto.ReorderPracticeAreasRequestDTO;
import de.tum.cit.aet.hephaestus.practices.dto.UpdatePracticeAreaRequestDTO;
import de.tum.cit.aet.hephaestus.practices.dto.UpdatePracticeAutonomyRequestDTO;
import de.tum.cit.aet.hephaestus.practices.model.PracticeArea;
import de.tum.cit.aet.hephaestus.practices.observation.PracticeAreaStatusService;
import de.tum.cit.aet.hephaestus.workspace.authorization.RequireAtLeastWorkspaceAdmin;
import de.tum.cit.aet.hephaestus.workspace.context.WorkspaceContext;
import de.tum.cit.aet.hephaestus.workspace.context.WorkspaceScopedController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * Controller for CRUD management of practice areas — the configurable groupings that organise
 * practices into learning objectives.
 *
 * <p>All endpoints are workspace-scoped. Read operations require workspace membership; write
 * operations require workspace admin or owner role. Binding a practice to an area lives on the
 * practice resource ({@code PUT /practices/{slug}/area}).
 */
@WorkspaceScopedController
@RequestMapping("/practice-areas")
@Tag(name = "Practice Areas", description = "Manage practice areas")
@RequiredArgsConstructor
@Validated
public class PracticeAreaController {

    private final PracticeAreaService areaService;
    private final PracticeAreaStatusService practiceAreaStatusService;
    private final CatalogOriginPresenter presenter;

    @GetMapping
    @Operation(summary = "List practice areas", description = "Returns the workspace's practice areas")
    @ApiResponse(
        responseCode = "200",
        description = "Areas returned",
        content = @Content(array = @ArraySchema(schema = @Schema(implementation = PracticeAreaDTO.class)))
    )
    @SecurityRequirements
    public ResponseEntity<List<PracticeAreaDTO>> listAreas(
        WorkspaceContext workspaceContext,
        @RequestParam(name = "visibleInPracticeDashboardsOnly", required = false) @Parameter(
            description = "Return only areas shown in practice dashboards"
        ) Boolean visibleInPracticeDashboardsOnly
    ) {
        List<PracticeAreaDTO> areas = presenter.presentAreas(
            workspaceContext.id(),
            areaService.listAreas(workspaceContext, visibleInPracticeDashboardsOnly)
        );
        return ResponseEntity.ok(areas);
    }

    @GetMapping("/{areaSlug}")
    @Operation(summary = "Get a practice area")
    @ApiResponse(
        responseCode = "200",
        description = "Area returned",
        content = @Content(schema = @Schema(implementation = PracticeAreaDTO.class))
    )
    @ApiResponse(
        responseCode = "404",
        description = "Area not found",
        content = @Content(schema = @Schema(hidden = true))
    )
    @SecurityRequirements
    public ResponseEntity<PracticeAreaDTO> getArea(WorkspaceContext workspaceContext, @PathVariable String areaSlug) {
        return ResponseEntity.ok(
            presenter.present(workspaceContext.id(), areaService.getArea(workspaceContext, areaSlug))
        );
    }

    @GetMapping("/status")
    @Operation(
        summary = "Derived practice-area statuses for the current developer",
        description = "Aggregates the authenticated developer's latest-run findings across every active practice " +
            "area into qualitative statuses, with supporting feedback items attached so each status stays " +
            "traceable to real feedback. Without a displayable finding the status reports why: NOT_OBSERVED " +
            "or NO_OPPORTUNITY."
    )
    @ApiResponse(
        responseCode = "200",
        description = "Active-area statuses returned",
        content = @Content(array = @ArraySchema(schema = @Schema(implementation = PracticeAreaStatusDTO.class)))
    )
    @SecurityRequirements
    public ResponseEntity<List<PracticeAreaStatusDTO>> getPracticeAreaStatuses(WorkspaceContext workspaceContext) {
        List<PracticeArea> activeAreas = areaService.listAreas(workspaceContext, true);
        return ResponseEntity.ok(practiceAreaStatusService.getAreaStatuses(workspaceContext.id(), activeAreas));
    }

    @PostMapping
    @Operation(summary = "Create a new practice area")
    @ApiResponse(
        responseCode = "201",
        description = "Area created",
        content = @Content(schema = @Schema(implementation = PracticeAreaDTO.class))
    )
    @ApiResponse(
        responseCode = "409",
        description = "Area slug already exists in this workspace",
        content = @Content(schema = @Schema(hidden = true))
    )
    @RequireAtLeastWorkspaceAdmin
    @Audited(ledger = AuditLedger.CONFIG_AUDIT, type = "PRACTICE_AREA")
    public ResponseEntity<PracticeAreaDTO> createArea(
        WorkspaceContext workspaceContext,
        @Valid @RequestBody CreatePracticeAreaRequestDTO request
    ) {
        PracticeArea area = areaService.createArea(
            workspaceContext,
            request.slug(),
            new AreaAttributes(
                request.name(),
                request.description(),
                request.displayOrder(),
                request.icon(),
                request.color()
            )
        );
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
            .path("/{slug}")
            .buildAndExpand(area.getSlug())
            .toUri();
        return ResponseEntity.created(location).body(presenter.present(workspaceContext.id(), area));
    }

    @PatchMapping("/{areaSlug}")
    @Operation(summary = "Update a practice area")
    @ApiResponse(
        responseCode = "200",
        description = "Area updated",
        content = @Content(schema = @Schema(implementation = PracticeAreaDTO.class))
    )
    @ApiResponse(
        responseCode = "404",
        description = "Area not found",
        content = @Content(schema = @Schema(hidden = true))
    )
    @RequireAtLeastWorkspaceAdmin
    @Audited(ledger = AuditLedger.CONFIG_AUDIT, type = "PRACTICE_AREA")
    public ResponseEntity<PracticeAreaDTO> updateArea(
        WorkspaceContext workspaceContext,
        @PathVariable String areaSlug,
        @Valid @RequestBody UpdatePracticeAreaRequestDTO request
    ) {
        PracticeArea area = areaService.updateArea(
            workspaceContext,
            areaSlug,
            new AreaAttributes(
                request.name(),
                request.description(),
                request.displayOrder(),
                request.icon(),
                request.color()
            ),
            request.visibleInPracticeDashboards()
        );
        return ResponseEntity.ok(presenter.present(workspaceContext.id(), area));
    }

    @PatchMapping("/{areaSlug}/autonomy")
    @Operation(
        summary = "Set how much autonomy the system has over one area",
        description = "Applies to every practice in the area that holds no autonomy of its own; practices that " +
            "set their own are left alone. OFF stops their reviews entirely. HUMAN_APPROVAL runs them and records " +
            "every observation, and holds feedback for an authorized reviewer. AUTOMATIC sends feedback without asking. Send a null " +
            "autonomy to clear the area's own setting so it follows the workspace default."
    )
    @ApiResponse(
        responseCode = "200",
        description = "Autonomy updated; the response carries the autonomy now in force and where it came from",
        content = @Content(schema = @Schema(implementation = PracticeAreaDTO.class))
    )
    @ApiResponse(
        responseCode = "404",
        description = "Area not found",
        content = @Content(schema = @Schema(hidden = true))
    )
    @RequireAtLeastWorkspaceAdmin
    @Audited(ledger = AuditLedger.CONFIG_AUDIT, type = "PRACTICE_AREA")
    public ResponseEntity<PracticeAreaDTO> setAreaAutonomy(
        WorkspaceContext workspaceContext,
        @PathVariable String areaSlug,
        @Valid @RequestBody UpdatePracticeAutonomyRequestDTO request
    ) {
        PracticeArea area = areaService.setAutonomy(workspaceContext, areaSlug, request.autonomy());
        return ResponseEntity.ok(presenter.present(workspaceContext.id(), area));
    }

    @PatchMapping("/reorder")
    @Operation(
        summary = "Reorder practice areas",
        description = "Sets each area's display order to its index in the provided slug list (one atomic write)"
    )
    @ApiResponse(
        responseCode = "200",
        description = "Areas reordered; the full ordered list is returned",
        content = @Content(array = @ArraySchema(schema = @Schema(implementation = PracticeAreaDTO.class)))
    )
    @ApiResponse(
        responseCode = "400",
        description = "orderedSlugs is empty or contains duplicates",
        content = @Content(schema = @Schema(hidden = true))
    )
    @ApiResponse(
        responseCode = "404",
        description = "A slug is unknown",
        content = @Content(schema = @Schema(hidden = true))
    )
    @RequireAtLeastWorkspaceAdmin
    @AuditExempt(reason = "catalog order affects presentation, not review execution or delivery")
    public ResponseEntity<List<PracticeAreaDTO>> reorderAreas(
        WorkspaceContext workspaceContext,
        @Valid @RequestBody ReorderPracticeAreasRequestDTO request
    ) {
        areaService.reorder(workspaceContext, request.orderedSlugs());
        List<PracticeAreaDTO> areas = presenter.presentAreas(
            workspaceContext.id(),
            areaService.listAreas(workspaceContext, null)
        );
        return ResponseEntity.ok(areas);
    }

    @DeleteMapping("/{areaSlug}")
    @Operation(
        summary = "Delete a practice area",
        description = "Moves its practices to Unassigned, then deletes the area"
    )
    @ApiResponse(responseCode = "204", description = "Area deleted")
    @ApiResponse(
        responseCode = "404",
        description = "Area not found",
        content = @Content(schema = @Schema(hidden = true))
    )
    @RequireAtLeastWorkspaceAdmin
    @Audited(ledger = AuditLedger.CONFIG_AUDIT, type = "PRACTICE_AREA")
    public ResponseEntity<Void> deleteArea(WorkspaceContext workspaceContext, @PathVariable String areaSlug) {
        areaService.deleteArea(workspaceContext, areaSlug);
        return ResponseEntity.noContent().build();
    }
}
