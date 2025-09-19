package de.tum.`in`.www1.hephaestus.leaderboard

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.Instant

@RestController
@RequestMapping("/team-leaderboard")
class TeamLeaderboardController {

    @Autowired
    lateinit var teamLeaderboardService: TeamLeaderboardService

    @GetMapping
    fun getTeamLeaderboard(
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) after: Instant,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) before: Instant,
        @RequestParam team: String?,
        @RequestParam sort: LeaderboardSortType?,
    ): ResponseEntity<MutableList<TeamLeaderboardEntryDTO>> {

        return ResponseEntity.ok(
            teamLeaderboardService.createTeamLeaderboard(
                after,
                before,
                team,
                sort
            )
        )
    }

    @GetMapping("/all-time")
    fun getAllTimeTeamLeaderboard(): ResponseEntity<MutableList<TeamLeaderboardEntryDTO>> {
        // Calculate or fetch the all-time leaderboard here
        return getTeamLeaderboard(
            Instant.parse("1970-01-01T00:00:00Z"),
            Instant.now().plusSeconds(600),
            "",
            LeaderboardSortType.SCORE
        )
    }
}