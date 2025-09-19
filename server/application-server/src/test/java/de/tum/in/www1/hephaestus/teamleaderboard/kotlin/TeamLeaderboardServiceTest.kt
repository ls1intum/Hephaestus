package de.tum.`in`.www1.hephaestus.teamleaderboard.kotlin

import de.tum.`in`.www1.hephaestus.gitprovider.team.Team
import de.tum.`in`.www1.hephaestus.gitprovider.team.TeamRepository
import de.tum.`in`.www1.hephaestus.gitprovider.user.User
import de.tum.`in`.www1.hephaestus.leaderboard.TeamLeaderboardService
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import java.time.Instant

class TeamLeaderboardServiceTest {

    private val teamRepository = mock(TeamRepository::class.java)
    private val service = TeamLeaderboardService()

    init {
        val field = TeamLeaderboardService::class.java.getDeclaredField("teamRepository")
        field.isAccessible = true
        field.set(service, teamRepository)
    }

    @Test
    fun createTeamLeaderboardTest() {
        val users = (1L..3L).map { id ->
            User().apply { this.id = id; this.login = "user$id" }
        }
        val teams = listOf(
            Team().apply { this.id = 1L; this.name = "TeamA" },
            Team().apply { this.id = 2L; this.name = "TeamB" }
        )
        val (user1, user2, user3) = users
        val (teamA, teamB) = teams
        `when`(teamRepository.findAll()).thenReturn(listOf(teamA, teamB))

        service.createTeamLeaderboard(Instant.now().minusSeconds(86400), Instant.now())

        // You can verify the logger output or, better, refactor the code to expose usersByTeam for testing.
        // For now, just verify that findAll was called.
        verify(teamRepository).findAll()
    }

}