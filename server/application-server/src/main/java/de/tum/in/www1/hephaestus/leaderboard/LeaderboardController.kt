package de.tum.`in`.www1.hephaestus.leaderboard

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
        @RequestParam(required = false) team: String?,
        @RequestParam(required = false) sort: LeaderboardSortType?,
        @RequestParam(required = false) mode: LeaderboardMode?,
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
