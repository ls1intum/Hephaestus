package de.tum.in.www1.hephaestus.achievement;

import de.tum.in.www1.hephaestus.core.exception.AccessForbiddenException;
import de.tum.in.www1.hephaestus.core.exception.EntityNotFoundException;
import de.tum.in.www1.hephaestus.gitprovider.user.User;
import de.tum.in.www1.hephaestus.gitprovider.user.UserRepository;
import de.tum.in.www1.hephaestus.workspace.context.WorkspaceContext;
import de.tum.in.www1.hephaestus.workspace.context.WorkspaceScopedController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Controller for user achievement endpoints.
 *
 * <p>Provides workspace-scoped access to user achievements with progress tracking.
 * Achievements are global to users (not workspace-specific), but this endpoint
 * respects workspace membership for access control.
 */
@WorkspaceScopedController
@RequestMapping("/users/{login}/achievements")
@Tag(name = "Achievements", description = "User achievement progress and unlocks")
@RequiredArgsConstructor
public class AchievementController {

    private static final Logger log = LoggerFactory.getLogger(AchievementController.class);

    private final AchievementService achievementService;
    private final UserRepository userRepository;
    private final Environment env;

    /**
     * Get all achievements with progress for a specific user.
     *
     * <p>Returns a complete list of all achievements with:
     * <ul>
     *   <li>Static metadata (name, description, icon, category)</li>
     *   <li>User-specific progress (current count vs required)</li>
     *   <li>Status (LOCKED, AVAILABLE, or UNLOCKED)</li>
     *   <li>Unlock timestamp if already earned</li>
     * </ul>
     *
     * <p>Achievements are global to users, not workspace-specific. Activity counts
     * across all workspaces contribute to achievement progress.
     *
     * @param workspaceContext the resolved workspace context (used for access control)
     * @param login the user's GitHub login
     * @return list of all achievements with user-specific progress
     * @throws EntityNotFoundException if user not found
     */
    @GetMapping
    @Operation(
        summary = "Get user achievements",
        description = "Returns all achievements with progress information for the specified user"
    )
    @SecurityRequirements
    public ResponseEntity<List<AchievementDTO>> getUserAchievements(
        WorkspaceContext workspaceContext,
        @PathVariable String login
    ) {
        log.debug("Getting achievements for user: {} in workspace: {}", login, workspaceContext.slug());

        User user = userRepository.findByLogin(login).orElseThrow(() -> new EntityNotFoundException("User", login));

        List<AchievementDTO> achievements = achievementService.getAllAchievementsWithProgress(user);
        return ResponseEntity.ok(achievements);
    }

    /**
     * Get all achievement definitions (Master List).
     * Restricted to development environments.
     */
    @GetMapping("/definitions")
    @Operation(
        summary = "Get all achievement definitions",
        description = "Returns the master list of all available achievements. Restricted to non-prod environments."
    )
    @SecurityRequirements
    public ResponseEntity<List<AchievementDTO>> getAllAchievementDefinitions(
        WorkspaceContext workspaceContext,
        @PathVariable String login
    ) {
        boolean isProd = Arrays.asList(env.getActiveProfiles()).contains("prod");
        if (isProd) {
            throw new AccessForbiddenException("Designer mode endpoints are restricted to development environments.");
        }
        log.debug("Getting all achievement definitions for designer mode in workspace: {}", workspaceContext.slug());

        List<AchievementDTO> definitions = achievementService.getAllAchievementDefinitions();
        return ResponseEntity.ok(definitions);
    }
}
