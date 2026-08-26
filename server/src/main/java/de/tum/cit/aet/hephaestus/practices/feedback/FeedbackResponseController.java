package de.tum.cit.aet.hephaestus.practices.feedback;

import de.tum.cit.aet.hephaestus.practices.feedback.dto.FeedbackEngagementDTO;
import de.tum.cit.aet.hephaestus.practices.feedback.dto.FeedbackResponseDTO;
import de.tum.cit.aet.hephaestus.practices.feedback.dto.FeedbackResponseRequestDTO;
import de.tum.cit.aet.hephaestus.workspace.context.WorkspaceContext;
import de.tum.cit.aet.hephaestus.workspace.context.WorkspaceScopedController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

/** One recipient-facing API for assessing and resolving delivered feedback. */
@WorkspaceScopedController
@PreAuthorize("@workspaceSecure.isMember()")
@RequestMapping("/practices/feedback")
@Tag(name = "Feedback Response", description = "Recipient responses to delivered practice feedback")
@RequiredArgsConstructor
public class FeedbackResponseController {

    private final FeedbackResponseService responseService;

    @PostMapping("/{feedbackId}/response")
    @Operation(
        operationId = "submitFeedbackResponse",
        summary = "Respond to delivered feedback",
        description = "Records usefulness, resolution, or both as a new immutable response snapshot."
    )
    @ApiResponse(responseCode = "201", description = "Response recorded")
    @ApiResponse(
        responseCode = "400",
        description = "Invalid response",
        content = @Content(schema = @Schema(hidden = true))
    )
    public ResponseEntity<FeedbackResponseDTO> submit(
        WorkspaceContext workspaceContext,
        @PathVariable UUID feedbackId,
        @Valid @RequestBody FeedbackResponseRequestDTO request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(
            responseService.submitResponse(workspaceContext, feedbackId, request)
        );
    }

    @GetMapping("/{feedbackId}/response")
    @Operation(operationId = "getLatestFeedbackResponse", summary = "Get the latest feedback response")
    public ResponseEntity<FeedbackResponseDTO> latest(
        WorkspaceContext workspaceContext,
        @PathVariable UUID feedbackId
    ) {
        return responseService
            .getLatestResponse(workspaceContext, feedbackId)
            .map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @GetMapping("/engagement")
    @Operation(operationId = "getFeedbackEngagement", summary = "Get feedback resolution counts")
    public ResponseEntity<FeedbackEngagementDTO> engagement(WorkspaceContext workspaceContext) {
        return ResponseEntity.ok(responseService.getEngagement(workspaceContext));
    }
}
