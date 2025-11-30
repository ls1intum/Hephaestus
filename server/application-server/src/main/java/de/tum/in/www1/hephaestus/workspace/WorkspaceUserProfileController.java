package de.tum.in.www1.hephaestus.workspace;

import de.tum.in.www1.hephaestus.core.exception.EntityNotFoundException;
import de.tum.in.www1.hephaestus.gitprovider.user.UserProfileDTO;
import de.tum.in.www1.hephaestus.gitprovider.user.UserService;
import de.tum.in.www1.hephaestus.workspace.context.WorkspaceContext;
import de.tum.in.www1.hephaestus.workspace.context.WorkspaceScopedController;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Workspace-scoped user profile endpoint.
 * Returns user profile data filtered by workspace (open PRs, review activity, etc.)
 */
@WorkspaceScopedController
@RequestMapping("/user")
@RequiredArgsConstructor
public class WorkspaceUserProfileController {

    private final UserService userService;

    /**
     * Get a user's profile with workspace-scoped activity data.
     *
     * @param workspaceContext the resolved workspace context
     * @param login the user's GitHub login
     * @return user profile with open PRs, review activity, etc.
     * @throws EntityNotFoundException if user not found
     */
    @GetMapping("/{login}/profile")
    @Operation(summary = "Get user profile with workspace-scoped data (open PRs, review activity, etc.)")
    public ResponseEntity<UserProfileDTO> getUserProfile(
        WorkspaceContext workspaceContext,
        @PathVariable String login
    ) {
        return ResponseEntity.ok(
            userService
                .getUserProfile(login, workspaceContext.id())
                .orElseThrow(() -> new EntityNotFoundException("User", login))
        );
    }
}
