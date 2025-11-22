package de.tum.in.www1.hephaestus.leaderboard;

import de.tum.in.www1.hephaestus.workspace.context.WorkspaceContext;
import de.tum.in.www1.hephaestus.workspace.context.WorkspaceScopedController;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@WorkspaceScopedController
@RequestMapping("/leaderboard")
public class LeaderboardController {

    private static final Logger logger = LoggerFactory.getLogger(LeaderboardController.class);

    private final LeaderboardService leaderboardService;

    public LeaderboardController(LeaderboardService leaderboardService) {
        this.leaderboardService = leaderboardService;
    }

    @GetMapping
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
        logger.info("Generating {} leaderboard for workspace {}", mode, workspaceContext.slug());
        return ResponseEntity.ok(leaderboardService.createLeaderboard(after, before, team, sort, mode));
    }

    @PostMapping
    public ResponseEntity<LeagueChangeDTO> getUserLeagueStats(
        WorkspaceContext workspaceContext,
        @RequestParam String login,
        @RequestBody LeaderboardEntryDTO entry
    ) {
        logger.info("Calculating league stats for user {} in workspace {}", login, workspaceContext.slug());
        return ResponseEntity.ok(leaderboardService.getUserLeagueStats(login, entry));
    }
}
