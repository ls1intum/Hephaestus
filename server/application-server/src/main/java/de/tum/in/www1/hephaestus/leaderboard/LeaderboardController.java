package de.tum.in.www1.hephaestus.leaderboard;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.format.annotation.DateTimeFormat.ISO;
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

    @Autowired
    private LeaderboardService leaderboardService;

    @GetMapping
    public ResponseEntity<List<LeaderboardEntryDTO>> getLeaderboard(
        @RequestParam @DateTimeFormat(iso = ISO.DATE_TIME) Instant after,
        @RequestParam @DateTimeFormat(iso = ISO.DATE_TIME) Instant before,
        @RequestParam Optional<String> team,
        @RequestParam Optional<LeaderboardSortType> sort
    ) {
        return ResponseEntity.ok(leaderboardService.createLeaderboard(after, before, team, sort));
    }

    @PostMapping
    public ResponseEntity<LeagueChangeDTO> getUserLeagueStats(
        @RequestParam String login,
        @RequestBody LeaderboardEntryDTO entry
    ) {
        return ResponseEntity.ok(leaderboardService.getUserLeagueStats(login, entry));
    }
}
