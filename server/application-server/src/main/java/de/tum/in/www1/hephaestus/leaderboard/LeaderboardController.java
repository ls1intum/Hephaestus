package de.tum.in.www1.hephaestus.leaderboard;

import de.tum.in.www1.hephaestus.core.LoggingUtils;
import de.tum.in.www1.hephaestus.workspace.Workspace;
import de.tum.in.www1.hephaestus.workspace.context.WorkspaceContext;
import de.tum.in.www1.hephaestus.workspace.context.WorkspaceContextResolver;
import de.tum.in.www1.hephaestus.workspace.context.WorkspaceScopedController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Controller for leaderboard generation and league statistics.
 *
 * <p>Provides ranked contributor lists based on configurable time ranges,
 * team filters, and scoring modes.
 */
@WorkspaceScopedController
@RequestMapping("/leaderboard")
@Tag(name = "Leaderboard", description = "Contributor rankings and league statistics")
@RequiredArgsConstructor
@Validated
public class LeaderboardController {

    private static final Logger logger = LoggerFactory.getLogger(LeaderboardController.class);

    private final LeaderboardService leaderboardService;
    private final WorkspaceContextResolver workspaceResolver;

    /**
     * Generate a leaderboard for the specified time range and filters.
     *
     * @param workspaceContext the resolved workspace context
     * @param after start of the time range (inclusive)
     * @param before end of the time range (inclusive)
     * @param team team filter for INDIVIDUAL mode ("all" for no filter)
     * @param sort sorting metric (SCORE or LEAGUE_POINTS)
     * @param mode aggregation mode (INDIVIDUAL or TEAM)
     * @return ranked list of leaderboard entries
     */
    @GetMapping
    @Operation(
        summary = "Generate leaderboard",
        description = "Creates a ranked contributor list for the specified time range"
    )
    public ResponseEntity<List<LeaderboardEntryDTO>> getLeaderboard(
        WorkspaceContext workspaceContext,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant after,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant before,
        @Parameter(
            description = "Team filter to apply in INDIVIDUAL mode; ignored when mode is TEAM."
        ) @RequestParam @NotBlank String team,
        @Parameter(
            description = "Determines the ranking metric. In TEAM mode SCORE uses summed contribution scores; LEAGUE_POINTS uses total league points."
        ) @RequestParam LeaderboardSortType sort,
        @RequestParam LeaderboardMode mode
    ) {
        logger.info(
            "Generating {} leaderboard for workspace {}",
            mode,
            LoggingUtils.sanitizeForLog(workspaceContext.slug())
        );
        Workspace workspace = workspaceResolver.requireWorkspace(workspaceContext);
        return ResponseEntity.ok(leaderboardService.createLeaderboard(workspace, after, before, team, sort, mode));
    }

    /**
     * Calculate league change statistics for a user.
     *
     * <p>This endpoint computes projected league point changes based on the provided
     * leaderboard entry. It does NOT modify any data - it's a calculation endpoint.
     *
     * @param workspaceContext the resolved workspace context
     * @param login the user's GitHub login
     * @param entry the user's current leaderboard entry for comparison
     * @return league change statistics including projected point delta
     */
    @PostMapping("/users/{login}/league-stats")
    @Operation(
        summary = "Calculate user league stats",
        description = "Computes projected league point changes for a specific user based on their leaderboard entry"
    )
    public ResponseEntity<LeagueChangeDTO> getUserLeagueStats(
        WorkspaceContext workspaceContext,
        @PathVariable String login,
        @RequestBody LeaderboardEntryDTO entry
    ) {
        logger.info(
            "Calculating league stats for user {} in workspace {}",
            LoggingUtils.sanitizeForLog(login),
            LoggingUtils.sanitizeForLog(workspaceContext.slug())
        );
        Workspace workspace = workspaceResolver.requireWorkspace(workspaceContext);
        return ResponseEntity.ok(leaderboardService.getUserLeagueStats(workspace, login, entry));
    }
}
