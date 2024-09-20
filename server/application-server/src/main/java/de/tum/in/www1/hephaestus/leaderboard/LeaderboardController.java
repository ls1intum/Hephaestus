package de.tum.in.www1.hephaestus.leaderboard;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/leaderboard")
public class LeaderboardController {

    private final LeaderboardService leaderboardService;

    public LeaderboardController(LeaderboardService leaderboardService) {
        this.leaderboardService = leaderboardService;
    }

    @GetMapping
    public ResponseEntity<List<LeaderboardEntry>> getLeaderboard(@RequestParam Optional<OffsetDateTime> after,
            @RequestParam Optional<OffsetDateTime> before) {
        return ResponseEntity.ok(leaderboardService.createLeaderboard(after, before));
    }
}
