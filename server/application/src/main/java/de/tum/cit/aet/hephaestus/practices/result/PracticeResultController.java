package de.tum.cit.aet.hephaestus.practices.result;

import de.tum.cit.aet.hephaestus.core.web.PageResponseDTO;
import de.tum.cit.aet.hephaestus.practices.result.dto.DeveloperPracticeSummaryDTO;
import de.tum.cit.aet.hephaestus.practices.result.dto.PracticeResultDetailDTO;
import de.tum.cit.aet.hephaestus.practices.result.dto.PracticeResultListDTO;
import de.tum.cit.aet.hephaestus.workspace.context.WorkspaceContext;
import de.tum.cit.aet.hephaestus.workspace.context.WorkspaceScopedController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Read-only REST API for practice results.
 *
 * <p>All endpoints require workspace membership (enforced by {@link WorkspaceScopedController}).
 * Every endpoint is scoped to the authenticated developer's own results.
 */
@WorkspaceScopedController
@RequestMapping("/practices/results")
@Tag(name = "Practice Results", description = "Read-only access to practice evaluation results")
@RequiredArgsConstructor
@Validated
public class PracticeResultController {

    private final PracticeResultService practiceResultService;

    @GetMapping
    @Operation(
            summary = "List results for current user",
            description = "Paginated results for the authenticated developer with optional filters")
    @ApiResponse(responseCode = "200", description = "Paginated results returned")
    public ResponseEntity<PageResponseDTO<PracticeResultListDTO>> listResults(
            WorkspaceContext workspaceContext, @Valid @ParameterObject ResultFeedFilterParams filter) {
        Pageable pageable = filter.pageable();

        Page<PracticeResultListDTO> practiceResults =
                practiceResultService.getPracticeResults(workspaceContext.id(), filter.toQuery(), pageable);
        return ResponseEntity.ok(PageResponseDTO.from(practiceResults));
    }

    @GetMapping("/summary")
    @Operation(
            summary = "Per-practice summary for current user",
            description = "Aggregated result counts per practice for dashboard cards")
    @ApiResponse(
            responseCode = "200",
            description = "Practice summaries returned",
            content =
                    @Content(
                            array = @ArraySchema(schema = @Schema(implementation = DeveloperPracticeSummaryDTO.class))))
    public ResponseEntity<List<DeveloperPracticeSummaryDTO>> getSummary(WorkspaceContext workspaceContext) {
        List<DeveloperPracticeSummaryDTO> summaries = practiceResultService.getSummary(workspaceContext.id()).stream()
                .map(DeveloperPracticeSummaryDTO::from)
                .toList();
        return ResponseEntity.ok(summaries);
    }

    @GetMapping("/{practiceResultId}")
    @Operation(summary = "Get result detail")
    @ApiResponse(
            responseCode = "200",
            description = "Result detail returned",
            content = @Content(schema = @Schema(implementation = PracticeResultDetailDTO.class)))
    @ApiResponse(
            responseCode = "404",
            description = "Result not found or not owned by current user",
            content = @Content(schema = @Schema(hidden = true)))
    public ResponseEntity<PracticeResultDetailDTO> getPracticeResult(
            WorkspaceContext workspaceContext, @PathVariable UUID practiceResultId) {
        return ResponseEntity.ok(
                practiceResultService.getPracticeResultDetail(workspaceContext.id(), practiceResultId));
    }
}
