package de.tum.in.www1.hephaestus.practices;

import de.tum.in.www1.hephaestus.practices.dto.BadPracticeFeedbackDTO;
import de.tum.in.www1.hephaestus.practices.feedback.BadPracticeFeedbackService;
import de.tum.in.www1.hephaestus.practices.model.PullRequestBadPractice;
import de.tum.in.www1.hephaestus.practices.model.PullRequestBadPracticeDTO;
import de.tum.in.www1.hephaestus.practices.model.PullRequestBadPracticeState;
import de.tum.in.www1.hephaestus.practices.model.PullRequestWithBadPracticesDTO;
import de.tum.in.www1.hephaestus.workspace.Workspace;
import de.tum.in.www1.hephaestus.workspace.context.WorkspaceContext;
import de.tum.in.www1.hephaestus.workspace.context.WorkspaceContextResolver;
import de.tum.in.www1.hephaestus.workspace.context.WorkspaceScopedController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Controller for bad practice resolution and feedback.
 *
 * <p>This controller handles the legacy bad practice resolution and feedback workflows.
 * It is scheduled for removal — new practice management is handled by {@code PracticeCatalogController}.
 *
 * <p>All endpoints require workspace membership via {@link WorkspaceScopedController}.
 *
 * @deprecated Scheduled for removal. Use the practice catalog and practice-aware review system instead.
 */
@Deprecated(forRemoval = true)
@WorkspaceScopedController
@RequestMapping("/bad-practices")
@RequiredArgsConstructor
@Tag(
    name = "Bad Practices (Deprecated)",
    description = "Legacy bad practice resolution — deprecated, scheduled for removal"
)
public class PracticesController {

    private final PracticesService practicesService;
    private final BadPracticeFeedbackService feedbackService;
    private final WorkspaceContextResolver workspaceResolver;

    // ══════════════════════════════════════════════════════════════════════════
    // GET endpoints - List and retrieve bad practices
    // ══════════════════════════════════════════════════════════════════════════

    @GetMapping("/user/{login}")
    @Operation(
        summary = "Get bad practices for a user",
        description = "Retrieves all detected bad practices for pull requests assigned to the user",
        deprecated = true
    )
    @ApiResponse(
        responseCode = "200",
        description = "Bad practices returned",
        content = @Content(schema = @Schema(implementation = UserPracticesDTO.class))
    )
    @SecurityRequirements
    public ResponseEntity<UserPracticesDTO> getBadPracticesForUser(
        WorkspaceContext workspaceContext,
        @PathVariable String login
    ) {
        Workspace workspace = workspaceResolver.requireWorkspace(workspaceContext);
        List<PullRequestWithBadPracticesDTO> pullRequests = practicesService.getBadPracticesForUser(workspace, login);
        return ResponseEntity.ok(new UserPracticesDTO(login, pullRequests));
    }

    @GetMapping("/pullrequest/{pullRequestId}")
    @Operation(
        summary = "Get bad practices for a pull request",
        description = "Retrieves all detected bad practices for a specific pull request",
        deprecated = true
    )
    @ApiResponse(
        responseCode = "200",
        description = "Bad practices returned",
        content = @Content(schema = @Schema(implementation = PullRequestWithBadPracticesDTO.class))
    )
    @SecurityRequirements
    public ResponseEntity<PullRequestWithBadPracticesDTO> getBadPracticesForPullRequest(
        WorkspaceContext workspaceContext,
        @PathVariable Long pullRequestId
    ) {
        Workspace workspace = workspaceResolver.requireWorkspace(workspaceContext);
        PullRequestWithBadPracticesDTO result = practicesService.getBadPracticesForPullRequest(
            workspace,
            pullRequestId
        );
        return ResponseEntity.ok(result);
    }

    @GetMapping("/badpractice/{id}")
    @Operation(
        summary = "Get a specific bad practice",
        description = "Retrieves details of a specific bad practice by ID",
        deprecated = true
    )
    @ApiResponse(
        responseCode = "200",
        description = "Bad practice returned",
        content = @Content(schema = @Schema(implementation = PullRequestBadPracticeDTO.class))
    )
    @SecurityRequirements
    public ResponseEntity<PullRequestBadPracticeDTO> getBadPractice(
        WorkspaceContext workspaceContext,
        @PathVariable Long id
    ) {
        Workspace workspace = workspaceResolver.requireWorkspace(workspaceContext);
        PullRequestBadPracticeDTO result = practicesService.getBadPractice(workspace, id);
        return ResponseEntity.ok(result);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // POST endpoints - Resolution and feedback
    // ══════════════════════════════════════════════════════════════════════════

    @PostMapping("/badpractice/{id}/resolve")
    @Operation(
        summary = "Resolve a bad practice",
        description = "Updates the state of a bad practice to FIXED, WONT_FIX, or WRONG",
        deprecated = true
    )
    @ApiResponse(responseCode = "200", description = "Bad practice resolved successfully")
    public ResponseEntity<Void> resolve(
        WorkspaceContext workspaceContext,
        @PathVariable Long id,
        @RequestParam("state") PullRequestBadPracticeState state
    ) {
        Workspace workspace = workspaceResolver.requireWorkspace(workspaceContext);
        practicesService.resolveBadPractice(workspace, id, state);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/badpractice/{id}/feedback")
    @Operation(
        summary = "Provide feedback for a bad practice",
        description = "Submits user feedback for a detected bad practice",
        deprecated = true
    )
    @ApiResponse(responseCode = "200", description = "Feedback submitted successfully")
    @ApiResponse(responseCode = "403", description = "User is not an assignee of the pull request")
    public ResponseEntity<Void> provideFeedback(
        WorkspaceContext workspaceContext,
        @PathVariable Long id,
        @RequestBody @Valid BadPracticeFeedbackDTO feedback
    ) {
        Workspace workspace = workspaceResolver.requireWorkspace(workspaceContext);
        PullRequestBadPractice badPractice = practicesService.getBadPracticeForFeedback(workspace, id);
        feedbackService.provideFeedback(workspace, badPractice, feedback);
        return ResponseEntity.ok().build();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // DTOs for API responses
    // ══════════════════════════════════════════════════════════════════════════

    /** Response for user bad practices listing */
    public record UserPracticesDTO(String login, List<PullRequestWithBadPracticesDTO> pullRequests) {}
}
