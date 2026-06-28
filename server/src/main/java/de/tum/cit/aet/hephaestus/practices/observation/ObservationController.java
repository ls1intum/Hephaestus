package de.tum.cit.aet.hephaestus.practices.observation;

import de.tum.cit.aet.hephaestus.core.exception.AccessForbiddenException;
import de.tum.cit.aet.hephaestus.practices.model.Presence;
import de.tum.cit.aet.hephaestus.practices.observation.dto.DeveloperPracticeSummaryDTO;
import de.tum.cit.aet.hephaestus.practices.observation.dto.ObservationDetailDTO;
import de.tum.cit.aet.hephaestus.practices.observation.dto.ObservationListDTO;
import de.tum.cit.aet.hephaestus.practices.observation.dto.ReflectionPracticeDTO;
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
import tools.jackson.databind.json.JsonMapper;

/**
 * Read-only REST API for practice observations.
 *
 * <p>All endpoints require workspace membership (enforced by {@link WorkspaceScopedController}).
 * List, summary, and detail endpoints are scoped to the authenticated developer's own
 * observations. The pull-request endpoint returns observations for all developers on that PR.
 */
@WorkspaceScopedController
@RequestMapping("/practices/observations")
@Tag(name = "Practice Observations", description = "Read-only access to practice evaluation observations")
@RequiredArgsConstructor
@Validated
public class ObservationController {

    private final ObservationService observationService;
    private final JsonMapper objectMapper;

    @GetMapping
    @Operation(
        summary = "List observations for current user",
        description = "Paginated observations for the authenticated developer with optional filters"
    )
    @ApiResponse(responseCode = "200", description = "Paginated observations returned")
    @SecurityRequirements
    public ResponseEntity<Page<ObservationListDTO>> listObservations(
        WorkspaceContext workspaceContext,
        @Parameter(description = "Filter by practice slug") @RequestParam(required = false) String practiceSlug,
        @Parameter(description = "Filter by presence") @RequestParam(required = false) Presence presence,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        int safePage = Math.max(page, 0);
        int pageSize = Math.max(1, Math.min(size, 100));
        Pageable pageable = PageRequest.of(safePage, pageSize, Sort.by("observedAt").descending());

        Page<ObservationListDTO> observations = observationService
            .getObservations(workspaceContext.id(), practiceSlug, presence, pageable)
            .map(ObservationListDTO::from);
        return ResponseEntity.ok(observations);
    }

    @GetMapping("/summary")
    @Operation(
        summary = "Per-practice summary for current user",
        description = "Aggregated observation counts per practice for dashboard cards"
    )
    @ApiResponse(
        responseCode = "200",
        description = "Practice summaries returned",
        content = @Content(array = @ArraySchema(schema = @Schema(implementation = DeveloperPracticeSummaryDTO.class)))
    )
    @SecurityRequirements
    public ResponseEntity<List<DeveloperPracticeSummaryDTO>> getSummary(WorkspaceContext workspaceContext) {
        List<DeveloperPracticeSummaryDTO> summaries = observationService
            .getSummary(workspaceContext.id())
            .stream()
            .map(DeveloperPracticeSummaryDTO::from)
            .toList();
        return ResponseEntity.ok(summaries);
    }

    @GetMapping("/reflection")
    @Operation(
        summary = "Reflective dashboard feedback for the current developer",
        description = "Per-practice cards a developer can READ — why the practice matters, what good looks like, " +
            "where they stand, the specific feedback to act on, and what they already do well. The third feedback " +
            "channel alongside in-context SCM notes and the conversational mentor; the same findings reorganised by " +
            "practice for self-paced reflection, not a scoreboard of counts."
    )
    @ApiResponse(
        responseCode = "200",
        description = "Per-practice reflection cards returned",
        content = @Content(array = @ArraySchema(schema = @Schema(implementation = ReflectionPracticeDTO.class)))
    )
    @SecurityRequirements
    public ResponseEntity<List<ReflectionPracticeDTO>> getReflection(WorkspaceContext workspaceContext) {
        return ResponseEntity.ok(observationService.getReflection(workspaceContext.id()));
    }

    @GetMapping("/{observationId}")
    @Operation(summary = "Get observation detail")
    @ApiResponse(
        responseCode = "200",
        description = "Observation detail returned",
        content = @Content(schema = @Schema(implementation = ObservationDetailDTO.class))
    )
    @ApiResponse(
        responseCode = "404",
        description = "Observation not found or not owned by current user",
        content = @Content(schema = @Schema(hidden = true))
    )
    @SecurityRequirements
    public ResponseEntity<ObservationDetailDTO> getObservation(
        WorkspaceContext workspaceContext,
        @PathVariable UUID observationId
    ) {
        var observation = observationService.getObservation(workspaceContext.id(), observationId);
        String deliveredGuidance = observationService.getDeliveredGuidance(observationId).orElse(null);
        return ResponseEntity.ok(ObservationDetailDTO.from(observation, deliveredGuidance, objectMapper));
    }

    @GetMapping("/pull-request/{prId}")
    @Operation(
        summary = "List observations for a pull request",
        description = "All observations for a specific pull request within the workspace"
    )
    @ApiResponse(
        responseCode = "200",
        description = "PR observations returned",
        content = @Content(array = @ArraySchema(schema = @Schema(implementation = ObservationListDTO.class)))
    )
    @SecurityRequirements
    public ResponseEntity<List<ObservationListDTO>> getObservationsForPullRequest(
        WorkspaceContext workspaceContext,
        @PathVariable Long prId
    ) {
        // Unlike the per-developer endpoints, this returns EVERY developer's BAD/ABSENT findings on the PR,
        // unscoped to the caller. On a public-read workspace an anonymous (membership-less) request would
        // otherwise expose them — require workspace membership.
        if (!workspaceContext.hasMembership()) {
            throw new AccessForbiddenException("Workspace membership is required to view pull-request observations");
        }
        List<ObservationListDTO> observations = observationService
            .getObservationsForPullRequest(workspaceContext.id(), prId)
            .stream()
            .map(ObservationListDTO::from)
            .toList();
        return ResponseEntity.ok(observations);
    }
}
