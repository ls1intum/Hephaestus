package de.tum.cit.aet.hephaestus.practices.review;

import de.tum.cit.aet.hephaestus.core.AuditLedger;
import de.tum.cit.aet.hephaestus.core.Audited;
import de.tum.cit.aet.hephaestus.workspace.authorization.RequireAtLeastWorkspaceAdmin;
import de.tum.cit.aet.hephaestus.workspace.context.WorkspaceContext;
import de.tum.cit.aet.hephaestus.workspace.context.WorkspaceScopedController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

@WorkspaceScopedController
@RequestMapping("/practices/review-settings")
@Tag(name = "Practice Review Settings", description = "Workspace-scoped practice-review policy")
@RequiredArgsConstructor
@Validated
public class PracticeReviewSettingsController {

    private final PracticeReviewSettingsService practiceReviewSettingsService;

    @GetMapping
    @Operation(summary = "Get the workspace's practice-review policy")
    @ApiResponse(
        responseCode = "200",
        description = "Policy returned",
        content = @Content(schema = @Schema(implementation = PracticeReviewSettingsDTO.class))
    )
    @RequireAtLeastWorkspaceAdmin
    public ResponseEntity<PracticeReviewSettingsDTO> getPracticeReviewSettings(WorkspaceContext workspaceContext) {
        return ResponseEntity.ok(practiceReviewSettingsService.getSettings(workspaceContext));
    }

    @PatchMapping
    @Operation(summary = "Update the workspace's practice-review policy")
    @ApiResponse(
        responseCode = "200",
        description = "Policy updated",
        content = @Content(schema = @Schema(implementation = PracticeReviewSettingsDTO.class))
    )
    @RequireAtLeastWorkspaceAdmin
    @Audited(ledger = AuditLedger.CONFIG_AUDIT, type = "PRACTICE_REVIEW_SETTINGS")
    public ResponseEntity<PracticeReviewSettingsDTO> updatePracticeReviewSettings(
        WorkspaceContext workspaceContext,
        @Valid @RequestBody UpdatePracticeReviewSettingsRequestDTO request
    ) {
        return ResponseEntity.ok(practiceReviewSettingsService.updatePracticeReview(workspaceContext, request));
    }
}
