package de.tum.in.www1.hephaestus.profile;

import de.tum.in.www1.hephaestus.core.exception.EntityNotFoundException;
import de.tum.in.www1.hephaestus.profile.dto.ProfileDTO;
import de.tum.in.www1.hephaestus.workspace.context.WorkspaceContext;
import de.tum.in.www1.hephaestus.workspace.context.WorkspaceScopedController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Controller for user profile endpoints.
 * Provides workspace-scoped user activity data (open PRs, review activity, league points).
 */
@WorkspaceScopedController
@RequestMapping("/profile")
@RequiredArgsConstructor
@Tag(name = "Profile", description = "User profile and activity data")
public class UserProfileController {

    private final UserProfileService userProfileService;

    /**
     * Get a user's profile with workspace-scoped activity data.
     *
     * @param workspaceContext the resolved workspace context
     * @param login the user's GitHub login
     * @return user profile with open PRs, review activity, league points, etc.
     * @throws EntityNotFoundException if user not found
     */
    @GetMapping("/{login}")
    @Operation(
        summary = "Get user profile",
        description = "Returns user profile with workspace-scoped activity data including open PRs, review activity, and league points"
    )
    @SecurityRequirements
    public ResponseEntity<ProfileDTO> getUserProfile(
        WorkspaceContext workspaceContext,
        @PathVariable String login,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant after,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant before
    ) {
        return ResponseEntity.ok(
            userProfileService
                .getUserProfile(login, workspaceContext.id(), after, before)
                .orElseThrow(() -> new EntityNotFoundException("User", login))
        );
    }
}
