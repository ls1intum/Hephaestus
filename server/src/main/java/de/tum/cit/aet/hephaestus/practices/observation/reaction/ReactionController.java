package de.tum.cit.aet.hephaestus.practices.observation.reaction;

import de.tum.cit.aet.hephaestus.practices.observation.reaction.dto.CreateReactionDTO;
import de.tum.cit.aet.hephaestus.practices.observation.reaction.dto.ReactionDTO;
import de.tum.cit.aet.hephaestus.practices.observation.reaction.dto.ReactionEngagementDTO;
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
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * REST controller for developer reactions to delivered units of feedback.
 *
 * <p>All endpoints are workspace-scoped and require authentication.
 * Reaction submission is restricted to the feedback's recipient.
 */
@WorkspaceScopedController
@RequestMapping("/practices/feedback")
@Tag(name = "Feedback Reaction", description = "Developer reactions to delivered feedback")
@RequiredArgsConstructor
@Validated
public class ReactionController {

    private final ReactionService reactionService;

    @PostMapping("/{feedbackId}/reactions")
    @Operation(
        summary = "Submit a reaction to a feedback unit",
        description = "Records the recipient's reaction (ADDRESSED, DISPUTED, NOT_APPLICABLE) to a delivered feedback unit. " +
            "Append-only: submitting again creates a new record, preserving temporal history."
    )
    @ApiResponse(
        responseCode = "201",
        description = "Reaction recorded",
        content = @Content(schema = @Schema(implementation = ReactionDTO.class))
    )
    @ApiResponse(
        responseCode = "400",
        description = "Invalid request (e.g., DISPUTED without explanation)",
        content = @Content(schema = @Schema(hidden = true))
    )
    @ApiResponse(
        responseCode = "403",
        description = "Current user is not the feedback's recipient",
        content = @Content(schema = @Schema(hidden = true))
    )
    @ApiResponse(
        responseCode = "404",
        description = "Feedback not found in this workspace",
        content = @Content(schema = @Schema(hidden = true))
    )
    public ResponseEntity<ReactionDTO> submitReaction(
        WorkspaceContext workspaceContext,
        @PathVariable UUID feedbackId,
        @Valid @RequestBody CreateReactionDTO request
    ) {
        ReactionDTO reaction = reactionService.submitReaction(workspaceContext, feedbackId, request);
        // No Location header: the append-only model has no per-reaction GET, so a resolvable
        // resource URI does not exist (the collection URI would be misleading per RFC 7231).
        return ResponseEntity.status(HttpStatus.CREATED).body(reaction);
    }

    @GetMapping("/{feedbackId}/reactions")
    @Operation(
        summary = "Get the latest reaction to a feedback unit",
        description = "Returns the current user's most recent reaction to the specified feedback unit, or 204 if none exists."
    )
    @ApiResponse(
        responseCode = "200",
        description = "Latest reaction returned",
        content = @Content(schema = @Schema(implementation = ReactionDTO.class))
    )
    @ApiResponse(
        responseCode = "204",
        description = "No reaction exists for this feedback unit",
        content = @Content(schema = @Schema(hidden = true))
    )
    @ApiResponse(
        responseCode = "404",
        description = "Feedback not found in this workspace",
        content = @Content(schema = @Schema(hidden = true))
    )
    public ResponseEntity<ReactionDTO> getLatestReaction(
        WorkspaceContext workspaceContext,
        @PathVariable UUID feedbackId
    ) {
        return reactionService
            .getLatestReaction(workspaceContext, feedbackId)
            .map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @GetMapping("/engagement")
    @Operation(
        summary = "Get engagement statistics",
        description = "Returns the current user's reaction action counts across all feedback they received in this workspace."
    )
    @ApiResponse(
        responseCode = "200",
        description = "Engagement statistics returned",
        content = @Content(schema = @Schema(implementation = ReactionEngagementDTO.class))
    )
    public ResponseEntity<ReactionEngagementDTO> getEngagement(WorkspaceContext workspaceContext) {
        ReactionEngagementDTO engagement = reactionService.getEngagement(workspaceContext);
        return ResponseEntity.ok(engagement);
    }
}
