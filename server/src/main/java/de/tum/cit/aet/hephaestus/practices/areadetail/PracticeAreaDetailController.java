package de.tum.cit.aet.hephaestus.practices.areadetail;

import de.tum.cit.aet.hephaestus.practices.areadetail.dto.PracticeAreaReviewHistoryPageDTO;
import de.tum.cit.aet.hephaestus.practices.observation.trend.dto.PracticeAreaTrendDTO;
import de.tum.cit.aet.hephaestus.workspace.context.WorkspaceContext;
import de.tum.cit.aet.hephaestus.workspace.context.WorkspaceScopedController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

@WorkspaceScopedController
@PreAuthorize("@workspaceSecure.isMember()")
@RequestMapping("/practice-areas/{areaSlug}")
@Tag(name = "Practice Area Detail", description = "Developer practice-area detail views")
@RequiredArgsConstructor
@Validated
public class PracticeAreaDetailController {

    private final PracticeAreaReviewHistoryService reviewHistoryService;
    private final PracticeAreaTrendQueryService trendQueryService;

    @GetMapping("/trend")
    @Operation(
        operationId = "getPracticeAreaTrend",
        summary = "Get the evidence-supported trend for a practice area",
        description = "Returns the area direction and every eligible practice direction with inspectable support."
    )
    @ApiResponse(responseCode = "200", description = "Practice-area trend returned")
    @ApiResponse(
        responseCode = "404",
        description = "Practice area not found",
        content = @Content(schema = @Schema(hidden = true))
    )
    @SecurityRequirements
    public ResponseEntity<PracticeAreaTrendDTO> getTrend(
        WorkspaceContext workspaceContext,
        @PathVariable String areaSlug
    ) {
        return ResponseEntity.ok(trendQueryService.get(workspaceContext, areaSlug));
    }

    @GetMapping("/review-history")
    @Operation(
        operationId = "listPracticeAreaReviewHistory",
        summary = "List complete review runs for a practice area",
        description = "Returns complete review runs newest first. Each moment contains the concrete positive and " +
            "negative observations that explain what the review observed."
    )
    @ApiResponse(responseCode = "200", description = "Paginated review runs returned")
    @ApiResponse(
        responseCode = "400",
        description = "Invalid filter or pagination",
        content = @Content(
            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
            schema = @Schema(implementation = ProblemDetail.class)
        )
    )
    @ApiResponse(
        responseCode = "404",
        description = "Practice area not found",
        content = @Content(schema = @Schema(hidden = true))
    )
    @SecurityRequirements
    public ResponseEntity<PracticeAreaReviewHistoryPageDTO> listReviewHistory(
        WorkspaceContext workspaceContext,
        @PathVariable String areaSlug,
        @Valid @ParameterObject PracticeAreaReviewHistoryFilterParams filter
    ) {
        return ResponseEntity.ok(
            reviewHistoryService.list(
                workspaceContext,
                areaSlug,
                filter.practiceSlug(),
                filter.kinds(),
                filter.severities(),
                filter.pageable()
            )
        );
    }
}
