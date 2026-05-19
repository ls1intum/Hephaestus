package de.tum.in.www1.hephaestus.practices;

import de.tum.in.www1.hephaestus.practices.dto.CreatePracticeRequestDTO;
import de.tum.in.www1.hephaestus.practices.dto.PracticeDTO;
import de.tum.in.www1.hephaestus.practices.dto.UpdatePracticeActiveRequestDTO;
import de.tum.in.www1.hephaestus.practices.dto.UpdatePracticeRequestDTO;
import de.tum.in.www1.hephaestus.practices.model.Practice;
import de.tum.in.www1.hephaestus.workspace.authorization.RequireAtLeastWorkspaceAdmin;
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
 * Controller for CRUD management of practice definitions.
 *
 * <p>All endpoints are workspace-scoped. Read operations require workspace membership;
 * write operations require workspace admin or owner role.
 */
@WorkspaceScopedController
@RequestMapping("/practices")
@Tag(name = "Practice Catalog", description = "Manage practice definitions")
@RequiredArgsConstructor
@Validated
public class PracticeCatalogController {

    private final PracticeService practiceService;

    @GetMapping
    @Operation(
        summary = "List practice definitions",
        description = "Returns all practice definitions for the workspace, optionally filtered by category and/or active state"
    )
    @ApiResponse(
        responseCode = "200",
        description = "Practices returned",
        content = @Content(array = @ArraySchema(schema = @Schema(implementation = PracticeDTO.class)))
    )
    @SecurityRequirements
    public ResponseEntity<List<PracticeDTO>> listPractices(
        WorkspaceContext workspaceContext,
        @RequestParam(required = false) @Parameter(description = "Filter by practice category") String category,
        @RequestParam(name = "active", required = false) @Parameter(
            description = "Filter by active state"
        ) Boolean active
    ) {
        List<PracticeDTO> practices = practiceService
            .listPractices(workspaceContext, category, active)
            .stream()
            .map(PracticeDTO::from)
            .toList();
        return ResponseEntity.ok(practices);
    }

    @GetMapping("/{practiceSlug}")
    @Operation(summary = "Get a practice definition")
    @ApiResponse(
        responseCode = "200",
        description = "Practice returned",
        content = @Content(schema = @Schema(implementation = PracticeDTO.class))
    )
    @ApiResponse(
        responseCode = "404",
        description = "Practice not found",
        content = @Content(schema = @Schema(hidden = true))
    )
    @SecurityRequirements
    public ResponseEntity<PracticeDTO> getPractice(
        WorkspaceContext workspaceContext,
        @PathVariable String practiceSlug
    ) {
        Practice practice = practiceService.getPractice(workspaceContext, practiceSlug);
        return ResponseEntity.ok(PracticeDTO.from(practice));
    }

    @PostMapping
    @Operation(summary = "Create a new practice definition")
    @ApiResponse(
        responseCode = "201",
        description = "Practice created",
        content = @Content(schema = @Schema(implementation = PracticeDTO.class))
    )
    @ApiResponse(
        responseCode = "409",
        description = "Practice slug already exists in this workspace",
        content = @Content(schema = @Schema(hidden = true))
    )
    @RequireAtLeastWorkspaceAdmin
    public ResponseEntity<PracticeDTO> createPractice(
        WorkspaceContext workspaceContext,
        @Valid @RequestBody CreatePracticeRequestDTO request
    ) {
        Practice practice = practiceService.createPractice(workspaceContext, request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
            .path("/{slug}")
            .buildAndExpand(practice.getSlug())
            .toUri();
        return ResponseEntity.created(location).body(PracticeDTO.from(practice));
    }

    @PatchMapping("/{practiceSlug}")
    @Operation(summary = "Update a practice definition")
    @ApiResponse(
        responseCode = "200",
        description = "Practice updated",
        content = @Content(schema = @Schema(implementation = PracticeDTO.class))
    )
    @ApiResponse(
        responseCode = "404",
        description = "Practice not found",
        content = @Content(schema = @Schema(hidden = true))
    )
    @RequireAtLeastWorkspaceAdmin
    public ResponseEntity<PracticeDTO> updatePractice(
        WorkspaceContext workspaceContext,
        @PathVariable String practiceSlug,
        @Valid @RequestBody UpdatePracticeRequestDTO request
    ) {
        Practice practice = practiceService.updatePractice(workspaceContext, practiceSlug, request);
        return ResponseEntity.ok(PracticeDTO.from(practice));
    }

    @PatchMapping("/{practiceSlug}/active")
    @Operation(summary = "Set practice active state")
    @ApiResponse(
        responseCode = "200",
        description = "Active state updated",
        content = @Content(schema = @Schema(implementation = PracticeDTO.class))
    )
    @ApiResponse(
        responseCode = "404",
        description = "Practice not found",
        content = @Content(schema = @Schema(hidden = true))
    )
    @RequireAtLeastWorkspaceAdmin
    public ResponseEntity<PracticeDTO> setActive(
        WorkspaceContext workspaceContext,
        @PathVariable String practiceSlug,
        @Valid @RequestBody UpdatePracticeActiveRequestDTO request
    ) {
        Practice practice = practiceService.setActive(workspaceContext, practiceSlug, request.active());
        return ResponseEntity.ok(PracticeDTO.from(practice));
    }

    @DeleteMapping("/{practiceSlug}")
    @Operation(summary = "Delete a practice definition")
    @ApiResponse(responseCode = "204", description = "Practice deleted")
    @ApiResponse(
        responseCode = "404",
        description = "Practice not found",
        content = @Content(schema = @Schema(hidden = true))
    )
    @RequireAtLeastWorkspaceAdmin
    public ResponseEntity<Void> deletePractice(WorkspaceContext workspaceContext, @PathVariable String practiceSlug) {
        practiceService.deletePractice(workspaceContext, practiceSlug);
        return ResponseEntity.noContent().build();
    }
}
