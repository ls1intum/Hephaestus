package de.tum.cit.aet.hephaestus.practices.feedback;

import de.tum.cit.aet.hephaestus.practices.feedback.dto.FeedbackHelpfulnessVoteDTO;
import de.tum.cit.aet.hephaestus.practices.feedback.dto.FeedbackHelpfulnessVoteRequestDTO;
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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

@WorkspaceScopedController
@RequestMapping("/practices/feedback/{feedbackId}/helpfulness")
@Tag(name = "Feedback Helpfulness", description = "Learner usefulness ratings for delivered practice feedback")
@RequiredArgsConstructor
public class FeedbackHelpfulnessVoteController {

    private final FeedbackHelpfulnessVoteService voteService;

    @PostMapping
    @Operation(operationId = "rateFeedbackHelpfulness", summary = "Rate delivered practice feedback")
    @ApiResponse(responseCode = "200", description = "Rating recorded")
    @ApiResponse(
        responseCode = "404",
        description = "Feedback not found",
        content = @Content(schema = @Schema(hidden = true))
    )
    public ResponseEntity<FeedbackHelpfulnessVoteDTO> rate(
        WorkspaceContext workspaceContext,
        @PathVariable UUID feedbackId,
        @Valid @RequestBody FeedbackHelpfulnessVoteRequestDTO request
    ) {
        return ResponseEntity.ok(voteService.upsert(workspaceContext, feedbackId, request.helpful()));
    }

    @DeleteMapping
    @Operation(operationId = "removeFeedbackHelpfulnessRating", summary = "Remove a usefulness rating")
    @ApiResponse(responseCode = "204", description = "Rating removed or did not exist")
    public ResponseEntity<Void> remove(WorkspaceContext workspaceContext, @PathVariable UUID feedbackId) {
        voteService.delete(workspaceContext, feedbackId);
        return ResponseEntity.noContent().build();
    }
}
