package de.tum.in.www1.hephaestus.workspace;

import de.tum.in.www1.hephaestus.gitprovider.user.UserProfileDTO;
import de.tum.in.www1.hephaestus.gitprovider.user.UserService;
import de.tum.in.www1.hephaestus.workspace.context.WorkspaceContext;
import de.tum.in.www1.hephaestus.workspace.context.WorkspaceScopedController;
import io.swagger.v3.oas.annotations.Operation;
import java.util.Optional;
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

    @GetMapping("/{login}/profile")
    @Operation(summary = "Get user profile with workspace-scoped data (open PRs, review activity, etc.)")
    public ResponseEntity<UserProfileDTO> getUserProfile(
        WorkspaceContext workspaceContext,
        @PathVariable String login
    ) {
        Optional<UserProfileDTO> userProfile = userService.getUserProfile(login, workspaceContext.id());
        return userProfile.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }
}
