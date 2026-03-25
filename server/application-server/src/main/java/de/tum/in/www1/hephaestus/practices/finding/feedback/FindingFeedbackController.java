package de.tum.in.www1.hephaestus.practices.finding.feedback;

import de.tum.in.www1.hephaestus.practices.finding.feedback.dto.CreateFindingFeedbackDTO;
import de.tum.in.www1.hephaestus.practices.finding.feedback.dto.FindingFeedbackDTO;
import de.tum.in.www1.hephaestus.practices.finding.feedback.dto.FindingFeedbackEngagementDTO;
import de.tum.in.www1.hephaestus.workspace.context.WorkspaceContext;
import de.tum.in.www1.hephaestus.workspace.context.WorkspaceScopedController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * REST controller for contributor feedback on AI-generated practice findings.
 *
 * <p>All endpoints are workspace-scoped and require authentication.
 * Feedback submission is restricted to the finding's contributor.
 */
@WorkspaceScopedController
@RequestMapping("/practices/findings")
@Tag(name = "Finding Feedback", description = "Contributor feedback on AI-generated practice findings")
@RequiredArgsConstructor
@Validated
public class FindingFeedbackController {

    private final FindingFeedbackService feedbackService;

    @PostMapping("/{findingId}/feedback")
    @Operation(
        summary = "Submit feedback on a practice finding",
        description = "Records the contributor's reaction (APPLIED, DISPUTED, NOT_APPLICABLE) to an AI-generated finding. " +
            "Append-only: submitting again creates a new record, preserving temporal history."
    )
    @ApiResponse(
        responseCode = "201",
        description = "Feedback recorded",
        content = @Content(schema = @Schema(implementation = FindingFeedbackDTO.class))
    )
    @ApiResponse(
        responseCode = "400",
        description = "Invalid request (e.g., DISPUTED without explanation)",
        content = @Content(schema = @Schema(hidden = true))
    )
    @ApiResponse(
        responseCode = "403",
        description = "Current user is not the finding's contributor",
        content = @Content(schema = @Schema(hidden = true))
    )
    @ApiResponse(
        responseCode = "404",
        description = "Finding not found in this workspace",
        content = @Content(schema = @Schema(hidden = true))
    )
    public ResponseEntity<FindingFeedbackDTO> submitFeedback(
        WorkspaceContext workspaceContext,
        @PathVariable UUID findingId,
        @Valid @RequestBody CreateFindingFeedbackDTO request
    ) {
        FindingFeedbackDTO feedback = feedbackService.submitFeedback(workspaceContext, findingId, request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().build().toUri();
        return ResponseEntity.created(location).body(feedback);
    }

    @GetMapping("/{findingId}/feedback")
    @Operation(
        summary = "Get latest feedback for a finding",
        description = "Returns the current user's most recent feedback on the specified finding, or 204 if none exists."
    )
    @ApiResponse(
        responseCode = "200",
        description = "Latest feedback returned",
        content = @Content(schema = @Schema(implementation = FindingFeedbackDTO.class))
    )
    @ApiResponse(
        responseCode = "204",
        description = "No feedback exists for this finding",
        content = @Content(schema = @Schema(hidden = true))
    )
    @ApiResponse(
        responseCode = "404",
        description = "Finding not found in this workspace",
        content = @Content(schema = @Schema(hidden = true))
    )
    public ResponseEntity<FindingFeedbackDTO> getLatestFeedback(
        WorkspaceContext workspaceContext,
        @PathVariable UUID findingId
    ) {
        return feedbackService
            .getLatestFeedback(workspaceContext, findingId)
            .map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @GetMapping("/engagement")
    @Operation(
        summary = "Get engagement statistics",
        description = "Returns the current user's feedback action counts across all findings in this workspace."
    )
    @ApiResponse(
        responseCode = "200",
        description = "Engagement statistics returned",
        content = @Content(schema = @Schema(implementation = FindingFeedbackEngagementDTO.class))
    )
    public ResponseEntity<FindingFeedbackEngagementDTO> getEngagement(WorkspaceContext workspaceContext) {
        FindingFeedbackEngagementDTO engagement = feedbackService.getEngagement(workspaceContext);
        return ResponseEntity.ok(engagement);
    }
}
