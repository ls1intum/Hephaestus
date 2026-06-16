package de.tum.cit.aet.hephaestus.practices;

import de.tum.cit.aet.hephaestus.practices.dto.CreatePracticeAreaRequestDTO;
import de.tum.cit.aet.hephaestus.practices.dto.PracticeAreaDTO;
import de.tum.cit.aet.hephaestus.practices.dto.ReorderPracticeAreasRequestDTO;
import de.tum.cit.aet.hephaestus.practices.dto.UpdatePracticeAreaRequestDTO;
import de.tum.cit.aet.hephaestus.practices.model.PracticeArea;
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
 * Controller for CRUD management of practice goals — the configurable groupings that organise
 * practices into learning objectives.
 *
 * <p>All endpoints are workspace-scoped. Read operations require workspace membership; write
 * operations require workspace admin or owner role. Binding a practice to a goal lives on the
 * practice resource ({@code PUT /practices/{slug}/goal}).
 */
@WorkspaceScopedController
@RequestMapping("/practice-goals")
@Tag(name = "Practice Goals", description = "Manage practice goals")
@RequiredArgsConstructor
@Validated
public class PracticeAreaController {

    private final PracticeAreaService goalService;

    @GetMapping
    @Operation(summary = "List practice goals", description = "Returns the workspace's practice goals")
    @ApiResponse(
        responseCode = "200",
        description = "Goals returned",
        content = @Content(array = @ArraySchema(schema = @Schema(implementation = PracticeAreaDTO.class)))
    )
    @SecurityRequirements
    public ResponseEntity<List<PracticeAreaDTO>> listGoals(
        WorkspaceContext workspaceContext,
        @RequestParam(name = "activeOnly", required = false) @Parameter(
            description = "Return only active goals"
        ) Boolean activeOnly
    ) {
        List<PracticeAreaDTO> goals = goalService
            .listGoals(workspaceContext, activeOnly)
            .stream()
            .map(PracticeAreaDTO::from)
            .toList();
        return ResponseEntity.ok(goals);
    }

    @GetMapping("/{goalSlug}")
    @Operation(summary = "Get a practice goal")
    @ApiResponse(
        responseCode = "200",
        description = "Goal returned",
        content = @Content(schema = @Schema(implementation = PracticeAreaDTO.class))
    )
    @ApiResponse(
        responseCode = "404",
        description = "Goal not found",
        content = @Content(schema = @Schema(hidden = true))
    )
    @SecurityRequirements
    public ResponseEntity<PracticeAreaDTO> getGoal(WorkspaceContext workspaceContext, @PathVariable String goalSlug) {
        return ResponseEntity.ok(PracticeAreaDTO.from(goalService.getGoal(workspaceContext, goalSlug)));
    }

    @PostMapping
    @Operation(summary = "Create a new practice goal")
    @ApiResponse(
        responseCode = "201",
        description = "Goal created",
        content = @Content(schema = @Schema(implementation = PracticeAreaDTO.class))
    )
    @ApiResponse(
        responseCode = "409",
        description = "Goal slug already exists in this workspace",
        content = @Content(schema = @Schema(hidden = true))
    )
    @RequireAtLeastWorkspaceAdmin
    public ResponseEntity<PracticeAreaDTO> createGoal(
        WorkspaceContext workspaceContext,
        @Valid @RequestBody CreatePracticeAreaRequestDTO request
    ) {
        PracticeArea goal = goalService.createGoal(
            workspaceContext,
            request.slug(),
            request.name(),
            request.description(),
            request.displayOrder() != null ? request.displayOrder() : 0
        );
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
            .path("/{slug}")
            .buildAndExpand(goal.getSlug())
            .toUri();
        return ResponseEntity.created(location).body(PracticeAreaDTO.from(goal));
    }

    @PatchMapping("/{goalSlug}")
    @Operation(summary = "Update a practice goal")
    @ApiResponse(
        responseCode = "200",
        description = "Goal updated",
        content = @Content(schema = @Schema(implementation = PracticeAreaDTO.class))
    )
    @ApiResponse(
        responseCode = "404",
        description = "Goal not found",
        content = @Content(schema = @Schema(hidden = true))
    )
    @RequireAtLeastWorkspaceAdmin
    public ResponseEntity<PracticeAreaDTO> updateGoal(
        WorkspaceContext workspaceContext,
        @PathVariable String goalSlug,
        @Valid @RequestBody UpdatePracticeAreaRequestDTO request
    ) {
        PracticeArea goal = goalService.updateGoal(
            workspaceContext,
            goalSlug,
            request.name(),
            request.description(),
            request.displayOrder()
        );
        if (request.active() != null) {
            goal = goalService.setActive(workspaceContext, goalSlug, request.active());
        }
        return ResponseEntity.ok(PracticeAreaDTO.from(goal));
    }

    @PatchMapping("/reorder")
    @Operation(
        summary = "Reorder practice goals",
        description = "Sets each goal's display order to its index in the provided slug list (one atomic write)"
    )
    @ApiResponse(
        responseCode = "200",
        description = "Goals reordered; the full ordered list is returned",
        content = @Content(array = @ArraySchema(schema = @Schema(implementation = PracticeAreaDTO.class)))
    )
    @ApiResponse(
        responseCode = "404",
        description = "A slug is unknown",
        content = @Content(schema = @Schema(hidden = true))
    )
    @RequireAtLeastWorkspaceAdmin
    public ResponseEntity<List<PracticeAreaDTO>> reorderGoals(
        WorkspaceContext workspaceContext,
        @Valid @RequestBody ReorderPracticeAreasRequestDTO request
    ) {
        goalService.reorder(workspaceContext, request.orderedSlugs());
        List<PracticeAreaDTO> goals = goalService
            .listGoals(workspaceContext, null)
            .stream()
            .map(PracticeAreaDTO::from)
            .toList();
        return ResponseEntity.ok(goals);
    }

    @DeleteMapping("/{goalSlug}")
    @Operation(
        summary = "Delete a practice goal",
        description = "Bound practices are unbound (their goal link is cleared), not deleted"
    )
    @ApiResponse(responseCode = "204", description = "Goal deleted")
    @ApiResponse(
        responseCode = "404",
        description = "Goal not found",
        content = @Content(schema = @Schema(hidden = true))
    )
    @RequireAtLeastWorkspaceAdmin
    public ResponseEntity<Void> deleteGoal(WorkspaceContext workspaceContext, @PathVariable String goalSlug) {
        goalService.deleteGoal(workspaceContext, goalSlug);
        return ResponseEntity.noContent().build();
    }
}
