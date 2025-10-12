package de.tum.`in`.www1.hephaestus.leaderboard

import io.swagger.v3.oas.annotations.Parameter
import java.time.Instant
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/leaderboard")
class LeaderboardController(
    private val leaderboardService: LeaderboardService,
) {

    @GetMapping
    fun getLeaderboard(
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) after: Instant,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) before: Instant,
        @Parameter(description = "Team filter to apply in INDIVIDUAL mode; ignored when mode is TEAM.")
        @RequestParam team: String,
        @Parameter(description = "Determines the ranking metric. In TEAM mode SCORE uses summed contribution scores; LEAGUE_POINTS uses total league points.")
        @RequestParam sort: LeaderboardSortType,
        @RequestParam mode: LeaderboardMode,
    ): ResponseEntity<List<LeaderboardEntryDTO>> {
        return ResponseEntity.ok(leaderboardService.createLeaderboard(after, before, team, sort, mode))
    }

    @PostMapping
    fun getUserLeagueStats(
        @RequestParam login: String,
        @RequestBody entry: LeaderboardEntryDTO,
    ): ResponseEntity<LeagueChangeDTO> {
        return ResponseEntity.ok(leaderboardService.getUserLeagueStats(login, entry))
    }
}
