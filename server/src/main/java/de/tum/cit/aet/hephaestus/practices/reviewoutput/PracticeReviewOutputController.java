package de.tum.cit.aet.hephaestus.practices.reviewoutput;

import de.tum.cit.aet.hephaestus.practices.reviewoutput.dto.ReviewFeedbackDTO;
import de.tum.cit.aet.hephaestus.practices.reviewoutput.dto.ReviewFeedbackDetailDTO;
import de.tum.cit.aet.hephaestus.practices.reviewoutput.dto.ReviewFindingDTO;
import de.tum.cit.aet.hephaestus.practices.reviewoutput.dto.ReviewFindingDetailDTO;
import de.tum.cit.aet.hephaestus.workspace.authorization.RequireAtLeastWorkspaceAdmin;
import de.tum.cit.aet.hephaestus.workspace.context.WorkspaceContext;
import de.tum.cit.aet.hephaestus.workspace.context.WorkspaceScopedController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.web.PagedModel;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@WorkspaceScopedController
@RequestMapping("/practices/reviews")
@Tag(
    name = "Practice Review Output",
    description = "Workspace-admin access to practice review runs, findings, and feedback delivery records"
)
@RequiredArgsConstructor
@Validated
@RequireAtLeastWorkspaceAdmin
public class PracticeReviewOutputController {

    private final ReviewFindingQueryService findingQueryService;
    private final ReviewFeedbackQueryService feedbackQueryService;

    @GetMapping("/findings")
    @Operation(
        summary = "List practice review findings across the workspace",
        description = "Results include linked feedback outcomes and are ordered newest first by default.",
        operationId = "listPracticeReviewFindings"
    )
    @ApiResponse(responseCode = "200", description = "Paginated findings returned")
    @ApiResponse(
        responseCode = "400",
        description = "Invalid filter or pagination",
        content = @Content(
            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
            schema = @Schema(implementation = ProblemDetail.class)
        )
    )
    public ResponseEntity<PagedModel<ReviewFindingDTO>> listFindings(
        WorkspaceContext workspaceContext,
        @RequestParam(defaultValue = "0") @Min(0) int page,
        @RequestParam(defaultValue = "50") @Min(1) @Max(100) int size,
        @Parameter(
            description = "Sorting strategy. ACTIONABILITY orders problems from CRITICAL to INFO, then strengths, " +
                "then not-applicable findings; ties are newest first."
        ) @RequestParam(defaultValue = "NEWEST") ReviewFindingSort sort,
        @Valid @ParameterObject ReviewFindingFilterParams filter
    ) {
        return ResponseEntity.ok(
            new PagedModel<>(
                findingQueryService.list(workspaceContext.id(), filter.toFilter(), sort, PageRequest.of(page, size))
            )
        );
    }

    @GetMapping("/findings/{findingId}")
    @Operation(
        summary = "Get a finding with its evidence and linked feedback",
        operationId = "getPracticeReviewFinding"
    )
    @ApiResponse(
        responseCode = "200",
        description = "Finding detail returned",
        content = @Content(schema = @Schema(implementation = ReviewFindingDetailDTO.class))
    )
    @ApiResponse(
        responseCode = "404",
        description = "Finding not found in this workspace",
        content = @Content(
            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
            schema = @Schema(implementation = ProblemDetail.class)
        )
    )
    public ResponseEntity<ReviewFindingDetailDTO> getFinding(
        WorkspaceContext workspaceContext,
        @PathVariable UUID findingId
    ) {
        return ResponseEntity.ok(findingQueryService.get(workspaceContext.id(), findingId));
    }

    @GetMapping("/feedback")
    @Operation(
        summary = "List practice review feedback across the workspace",
        description = "Results are ordered newest first and include every delivery state.",
        operationId = "listPracticeReviewFeedback"
    )
    @ApiResponse(responseCode = "200", description = "Paginated feedback units returned")
    @ApiResponse(
        responseCode = "400",
        description = "Invalid filter or pagination",
        content = @Content(
            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
            schema = @Schema(implementation = ProblemDetail.class)
        )
    )
    public ResponseEntity<PagedModel<ReviewFeedbackDTO>> listFeedback(
        WorkspaceContext workspaceContext,
        @RequestParam(defaultValue = "0") @Min(0) int page,
        @RequestParam(defaultValue = "50") @Min(1) @Max(100) int size,
        @Valid @ParameterObject ReviewFeedbackFilterParams filter
    ) {
        return ResponseEntity.ok(
            new PagedModel<>(
                feedbackQueryService.list(workspaceContext.id(), filter.toFilter(), PageRequest.of(page, size))
            )
        );
    }

    @GetMapping("/feedback/{feedbackId}")
    @Operation(
        summary = "Get feedback with its stored body, findings and placements",
        operationId = "getPracticeReviewFeedback"
    )
    @ApiResponse(
        responseCode = "200",
        description = "Feedback unit detail returned",
        content = @Content(schema = @Schema(implementation = ReviewFeedbackDetailDTO.class))
    )
    @ApiResponse(
        responseCode = "404",
        description = "Feedback not found in this workspace",
        content = @Content(
            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
            schema = @Schema(implementation = ProblemDetail.class)
        )
    )
    public ResponseEntity<ReviewFeedbackDetailDTO> getFeedback(
        WorkspaceContext workspaceContext,
        @PathVariable UUID feedbackId
    ) {
        return ResponseEntity.ok(feedbackQueryService.get(workspaceContext.id(), feedbackId));
    }
}
