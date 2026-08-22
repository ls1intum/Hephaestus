package de.tum.cit.aet.hephaestus.practices.feedback;

import de.tum.cit.aet.hephaestus.practices.feedback.dto.FeedbackRatingDTO;
import de.tum.cit.aet.hephaestus.practices.feedback.dto.FeedbackRatingRequestDTO;
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
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

@WorkspaceScopedController
@PreAuthorize("@workspaceSecure.isMember()")
@RequestMapping("/practices/feedback/{feedbackId}/rating")
@Tag(name = "Feedback Rating", description = "Recipient assessments of delivered practice feedback")
@RequiredArgsConstructor
public class FeedbackRatingController {

    private final FeedbackRatingService ratingService;

    @PutMapping
    @Operation(operationId = "setFeedbackRating", summary = "Create or replace a feedback rating")
    @ApiResponse(responseCode = "200", description = "Rating recorded")
    @ApiResponse(
        responseCode = "404",
        description = "Feedback not found",
        content = @Content(schema = @Schema(hidden = true))
    )
    public ResponseEntity<FeedbackRatingDTO> rate(
        WorkspaceContext workspaceContext,
        @PathVariable UUID feedbackId,
        @Valid @RequestBody FeedbackRatingRequestDTO request
    ) {
        return ResponseEntity.ok(
            ratingService.upsert(workspaceContext, feedbackId, request.state(), request.comment())
        );
    }

    @DeleteMapping
    @Operation(operationId = "removeFeedbackRating", summary = "Remove a feedback rating")
    @ApiResponse(responseCode = "204", description = "Rating removed or did not exist")
    public ResponseEntity<Void> remove(WorkspaceContext workspaceContext, @PathVariable UUID feedbackId) {
        ratingService.delete(workspaceContext, feedbackId);
        return ResponseEntity.noContent().build();
    }
}
