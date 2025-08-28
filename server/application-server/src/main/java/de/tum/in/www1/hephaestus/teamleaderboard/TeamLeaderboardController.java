package de.tum.in.www1.hephaestus.teamleaderboard;

import de.tum.in.www1.hephaestus.leaderboard.LeaderboardSortType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.format.annotation.DateTimeFormat.ISO;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import java.time.OffsetDateTime;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/team-leaderboard")
public class TeamLeaderboardController {

    @Autowired
    private TeamLeaderboardService teamLeaderboardService;

    @GetMapping
    public ResponseEntity<List<TeamLeaderboardEntryDTO>> getTeamLeaderboard(
        @RequestParam @DateTimeFormat(iso = ISO.DATE_TIME) OffsetDateTime after,
        @RequestParam @DateTimeFormat(iso = ISO.DATE_TIME) OffsetDateTime before,
        @RequestParam Optional<String> team,
        @RequestParam Optional<LeaderboardSortType> sort
        ) {
        return ResponseEntity.ok(teamLeaderboardService.createTeamLeaderboard(after, before, team, sort));
    }

}
