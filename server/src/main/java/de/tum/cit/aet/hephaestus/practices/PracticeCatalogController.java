package de.tum.cit.aet.hephaestus.practices;

import de.tum.cit.aet.hephaestus.core.AuditExempt;
import de.tum.cit.aet.hephaestus.core.AuditLedger;
import de.tum.cit.aet.hephaestus.core.Audited;
import de.tum.cit.aet.hephaestus.practices.dto.BindPracticeAreaRequestDTO;
import de.tum.cit.aet.hephaestus.practices.dto.CreatePracticeRequestDTO;
import de.tum.cit.aet.hephaestus.practices.dto.LearnerPracticeDTO;
import de.tum.cit.aet.hephaestus.practices.dto.PlacePracticeRequestDTO;
import de.tum.cit.aet.hephaestus.practices.dto.PracticeDTO;
import de.tum.cit.aet.hephaestus.practices.dto.ReorderPracticesRequestDTO;
import de.tum.cit.aet.hephaestus.practices.dto.UpdatePracticeActiveRequestDTO;
import de.tum.cit.aet.hephaestus.practices.dto.UpdatePracticeRequestDTO;
import de.tum.cit.aet.hephaestus.practices.model.Practice;
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
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@WorkspaceScopedController
@RequestMapping("/practices")
@Tag(name = "Practice Catalog", description = "Manage practice definitions")
@RequiredArgsConstructor
@Validated
public class PracticeCatalogController {

    private final PracticeService practiceService;
    private final PracticeAreaService areaService;

    @GetMapping
    @Operation(
        summary = "List practice definitions",
        description = "Returns all practice definitions for the workspace, optionally filtered by active state"
    )
    @ApiResponse(
        responseCode = "200",
        description = "Practices returned",
        content = @Content(array = @ArraySchema(schema = @Schema(implementation = PracticeDTO.class)))
    )
    @RequireAtLeastWorkspaceAdmin
    public ResponseEntity<List<PracticeDTO>> listPractices(
        WorkspaceContext workspaceContext,
        @RequestParam(name = "active", required = false) @Parameter(
            description = "Filter by active state"
        ) Boolean active
    ) {
        List<PracticeDTO> practices = practiceService
            .listPractices(workspaceContext, active)
            .stream()
            .map(PracticeDTO::from)
            .toList();
        return ResponseEntity.ok(practices);
    }

    @GetMapping("/learner")
    @Operation(
        summary = "List active practices, learner-facing",
        description = "Active practices projected for a developer: name, area, why-it-matters, what-good-looks-like." +
            " The detection criteria is ABSENT BY CONSTRUCTION (LearnerPracticeDTO has no such field)."
    )
    @ApiResponse(
        responseCode = "200",
        description = "Learner practices returned",
        content = @Content(array = @ArraySchema(schema = @Schema(implementation = LearnerPracticeDTO.class)))
    )
    @SecurityRequirements
    public ResponseEntity<List<LearnerPracticeDTO>> listLearnerPractices(WorkspaceContext workspaceContext) {
        List<LearnerPracticeDTO> practices = practiceService
            .listPractices(workspaceContext, true)
            .stream()
            .map(LearnerPracticeDTO::from)
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
        content = @Content(
            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
            schema = @Schema(implementation = ProblemDetail.class)
        )
    )
    @RequireAtLeastWorkspaceAdmin
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
        content = @Content(
            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
            schema = @Schema(implementation = ProblemDetail.class)
        )
    )
    @ApiResponse(
        responseCode = "404",
        description = "Practice area not found",
        content = @Content(
            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
            schema = @Schema(implementation = ProblemDetail.class)
        )
    )
    @RequireAtLeastWorkspaceAdmin
    @Audited(ledger = AuditLedger.CONFIG_AUDIT, type = "PRACTICE_DEFINITION")
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

    @PatchMapping("/reorder")
    @Operation(
        summary = "Reorder the practices within an area",
        description = "Sets each practice's display order to its index in the provided slug list (one atomic write)"
    )
    @ApiResponse(
        responseCode = "200",
        description = "Practices reordered; the full ordered practice list is returned",
        content = @Content(array = @ArraySchema(schema = @Schema(implementation = PracticeDTO.class)))
    )
    @ApiResponse(
        responseCode = "400",
        description = "orderedSlugs is empty, has duplicates, or is not the area's complete set",
        content = @Content(
            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
            schema = @Schema(implementation = ProblemDetail.class)
        )
    )
    @RequireAtLeastWorkspaceAdmin
    @AuditExempt(reason = "catalog order affects presentation, not review execution or delivery")
    public ResponseEntity<List<PracticeDTO>> reorderPractices(
        WorkspaceContext workspaceContext,
        @Valid @RequestBody ReorderPracticesRequestDTO request
    ) {
        practiceService.reorderPractices(workspaceContext, request.areaSlug(), request.orderedSlugs());
        List<PracticeDTO> practices = practiceService
            .listPractices(workspaceContext, null)
            .stream()
            .map(PracticeDTO::from)
            .toList();
        return ResponseEntity.ok(practices);
    }

    @PatchMapping("/{practiceSlug}")
    @Operation(summary = "Update a practice")
    @ApiResponse(
        responseCode = "200",
        description = "Practice updated",
        content = @Content(schema = @Schema(implementation = PracticeDTO.class))
    )
    @ApiResponse(
        responseCode = "404",
        description = "Practice or practice area not found",
        content = @Content(
            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
            schema = @Schema(implementation = ProblemDetail.class)
        )
    )
    @RequireAtLeastWorkspaceAdmin
    @Audited(ledger = AuditLedger.CONFIG_AUDIT, type = "PRACTICE_DEFINITION")
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
        content = @Content(
            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
            schema = @Schema(implementation = ProblemDetail.class)
        )
    )
    @RequireAtLeastWorkspaceAdmin
    @Audited(ledger = AuditLedger.CONFIG_AUDIT, type = "PRACTICE_ACTIVE")
    public ResponseEntity<PracticeDTO> setActive(
        WorkspaceContext workspaceContext,
        @PathVariable String practiceSlug,
        @Valid @RequestBody UpdatePracticeActiveRequestDTO request
    ) {
        Practice practice = practiceService.setActive(workspaceContext, practiceSlug, request.active());
        return ResponseEntity.ok(PracticeDTO.from(practice));
    }

    @PutMapping("/{practiceSlug}/area")
    @Operation(
        summary = "Move a practice",
        description = "Moves the practice to the requested area, or to Unassigned when areaSlug is null"
    )
    @ApiResponse(
        responseCode = "200",
        description = "Practice moved",
        content = @Content(schema = @Schema(implementation = PracticeDTO.class))
    )
    @ApiResponse(
        responseCode = "404",
        description = "Practice or area not found",
        content = @Content(
            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
            schema = @Schema(implementation = ProblemDetail.class)
        )
    )
    @RequireAtLeastWorkspaceAdmin
    @Audited(ledger = AuditLedger.CONFIG_AUDIT, type = "PRACTICE_DEFINITION")
    public ResponseEntity<PracticeDTO> bindArea(
        WorkspaceContext workspaceContext,
        @PathVariable String practiceSlug,
        @Valid @RequestBody BindPracticeAreaRequestDTO request
    ) {
        Practice practice = areaService.bindPractice(workspaceContext, practiceSlug, request.areaSlug());
        return ResponseEntity.ok(PracticeDTO.from(practice));
    }

    @PutMapping("/{practiceSlug}/placement")
    @Operation(
        summary = "Place a practice in the catalog",
        description = "Moves the practice and sets its exact position in one atomic write; omit areaSlug for Unassigned"
    )
    @ApiResponse(
        responseCode = "200",
        description = "Practice placed; the full updated practice list is returned",
        content = @Content(array = @ArraySchema(schema = @Schema(implementation = PracticeDTO.class)))
    )
    @ApiResponse(
        responseCode = "400",
        description = "position is missing, negative, or beyond the destination",
        content = @Content(
            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
            schema = @Schema(implementation = ProblemDetail.class)
        )
    )
    @ApiResponse(
        responseCode = "404",
        description = "Practice or area not found",
        content = @Content(
            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
            schema = @Schema(implementation = ProblemDetail.class)
        )
    )
    @RequireAtLeastWorkspaceAdmin
    @Audited(ledger = AuditLedger.CONFIG_AUDIT, type = "PRACTICE_DEFINITION")
    public ResponseEntity<List<PracticeDTO>> placePractice(
        WorkspaceContext workspaceContext,
        @PathVariable String practiceSlug,
        @Valid @RequestBody PlacePracticeRequestDTO request
    ) {
        List<PracticeDTO> practices = practiceService
            .placePractice(workspaceContext, practiceSlug, request.areaSlug(), request.position())
            .stream()
            .map(PracticeDTO::from)
            .toList();
        return ResponseEntity.ok(practices);
    }

    @DeleteMapping("/{practiceSlug}")
    @Operation(summary = "Delete a practice definition")
    @ApiResponse(responseCode = "204", description = "Practice deleted")
    @ApiResponse(
        responseCode = "404",
        description = "Practice not found",
        content = @Content(
            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
            schema = @Schema(implementation = ProblemDetail.class)
        )
    )
    @RequireAtLeastWorkspaceAdmin
    @Audited(ledger = AuditLedger.CONFIG_AUDIT, type = "PRACTICE_DEFINITION")
    public ResponseEntity<Void> deletePractice(WorkspaceContext workspaceContext, @PathVariable String practiceSlug) {
        practiceService.deletePractice(workspaceContext, practiceSlug);
        return ResponseEntity.noContent().build();
    }
}
