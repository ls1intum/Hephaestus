package de.tum.in.www1.hephaestus.leaderboard;

import io.swagger.v3.oas.annotations.Parameter;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/leaderboard")
public class LeaderboardController {

    private static final Logger logger = LoggerFactory.getLogger(LeaderboardController.class);

    @Autowired
    private LeaderboardService leaderboardService;

    public LeaderboardController(LeaderboardService leaderboardService) {
        this.leaderboardService = leaderboardService;
    }

    @GetMapping
    public ResponseEntity<List<LeaderboardEntryDTO>> getLeaderboard(
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant after,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant before,
        @Parameter(description = "Team filter to apply in INDIVIDUAL mode; ignored when mode is TEAM.")
        @RequestParam String team,
        @Parameter(description = "Determines the ranking metric. In TEAM mode SCORE uses summed contribution scores; LEAGUE_POINTS uses total league points.")
        @RequestParam LeaderboardSortType sort,
        @RequestParam LeaderboardMode mode
    ) {
        return ResponseEntity.ok(leaderboardService.createLeaderboard(after, before, team, sort, mode));
    }

    @PostMapping
    public ResponseEntity<LeagueChangeDTO> getUserLeagueStats(
        @RequestParam String login,
        @RequestBody LeaderboardEntryDTO entry
    ) {
        return ResponseEntity.ok(leaderboardService.getUserLeagueStats(login, entry));
    }
}