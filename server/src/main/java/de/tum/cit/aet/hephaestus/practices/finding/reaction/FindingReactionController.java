package de.tum.cit.aet.hephaestus.practices.finding.reaction;

import de.tum.cit.aet.hephaestus.practices.finding.reaction.dto.CreateFindingReactionDTO;
import de.tum.cit.aet.hephaestus.practices.finding.reaction.dto.FindingReactionDTO;
import de.tum.cit.aet.hephaestus.practices.finding.reaction.dto.FindingReactionEngagementDTO;
import de.tum.cit.aet.hephaestus.workspace.context.WorkspaceContext;
import de.tum.cit.aet.hephaestus.workspace.context.WorkspaceScopedController;
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
 * REST controller for contributor reaction on AI-generated practice findings.
 *
 * <p>All endpoints are workspace-scoped and require authentication.
 * Reaction submission is restricted to the finding's contributor.
 */
@WorkspaceScopedController
@RequestMapping("/practices/findings")
@Tag(name = "Finding Reaction", description = "Contributor reactions to AI-generated practice findings")
@RequiredArgsConstructor
@Validated
public class FindingReactionController {

    private final FindingReactionService reactionService;

    @PostMapping("/{findingId}/reactions")
    @Operation(
        summary = "Submit a reaction to a practice finding",
        description = "Records the contributor's reaction (APPLIED, DISPUTED, NOT_APPLICABLE) to an AI-generated finding. " +
            "Append-only: submitting again creates a new record, preserving temporal history."
    )
    @ApiResponse(
        responseCode = "201",
        description = "Reaction recorded",
        content = @Content(schema = @Schema(implementation = FindingReactionDTO.class))
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
    public ResponseEntity<FindingReactionDTO> submitReaction(
        WorkspaceContext workspaceContext,
        @PathVariable UUID findingId,
        @Valid @RequestBody CreateFindingReactionDTO request
    ) {
        FindingReactionDTO reaction = reactionService.submitReaction(workspaceContext, findingId, request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().build().toUri();
        return ResponseEntity.created(location).body(reaction);
    }

    @GetMapping("/{findingId}/reactions")
    @Operation(
        summary = "Get the latest reaction to a finding",
        description = "Returns the current user's most recent reaction to the specified finding, or 204 if none exists."
    )
    @ApiResponse(
        responseCode = "200",
        description = "Latest reaction returned",
        content = @Content(schema = @Schema(implementation = FindingReactionDTO.class))
    )
    @ApiResponse(
        responseCode = "204",
        description = "No reaction exists for this finding",
        content = @Content(schema = @Schema(hidden = true))
    )
    @ApiResponse(
        responseCode = "404",
        description = "Finding not found in this workspace",
        content = @Content(schema = @Schema(hidden = true))
    )
    public ResponseEntity<FindingReactionDTO> getLatestReaction(
        WorkspaceContext workspaceContext,
        @PathVariable UUID findingId
    ) {
        return reactionService
            .getLatestReaction(workspaceContext, findingId)
            .map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @GetMapping("/engagement")
    @Operation(
        summary = "Get engagement statistics",
        description = "Returns the current user's reaction action counts across all findings in this workspace."
    )
    @ApiResponse(
        responseCode = "200",
        description = "Engagement statistics returned",
        content = @Content(schema = @Schema(implementation = FindingReactionEngagementDTO.class))
    )
    public ResponseEntity<FindingReactionEngagementDTO> getEngagement(WorkspaceContext workspaceContext) {
        FindingReactionEngagementDTO engagement = reactionService.getEngagement(workspaceContext);
        return ResponseEntity.ok(engagement);
    }
}
