package de.tum.in.www1.hephaestus.leaderboard;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import de.tum.in.www1.hephaestus.leaderboard.dto.LeaderboardEntryDTO;

@RestController
@RequestMapping("/leaderboard")
public class LeaderboardController {

    private final LeaderboardService leaderboardService;

    public LeaderboardController(LeaderboardService leaderboardService) {
        this.leaderboardService = leaderboardService;
    }

    @GetMapping
    public ResponseEntity<List<LeaderboardEntryDTO>> getLeaderboard(
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") Optional<LocalDate> after,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") Optional<LocalDate> before,
            @RequestParam Optional<String> repository) {
        return ResponseEntity.ok(leaderboardService.createLeaderboard(after, before, repository));
    }
}
