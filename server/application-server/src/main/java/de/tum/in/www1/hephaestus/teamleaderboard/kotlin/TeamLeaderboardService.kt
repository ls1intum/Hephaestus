package de.tum.`in`.www1.hephaestus.teamleaderboard.kotlin

import de.tum.`in`.www1.hephaestus.gitprovider.team.TeamRepository
import de.tum.`in`.www1.hephaestus.leaderboard.LeaderboardSortType
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import java.time.OffsetDateTime
import java.util.*

@Service
class TeamLeaderboardService {

    companion object {
        private val logger = LoggerFactory.getLogger("TeamLeaderboardService")
    }

    @Autowired
    private val teamRepository: TeamRepository? = null

    // TODO: private attributes that are needed for the class to fulfill it's duty
    // ---
    // TODO: add the method bodies to the according functions
    fun createTeamLeaderboard(
        after: OffsetDateTime?,
        before: OffsetDateTime?,
        team: Optional<String>?,
        sort: Optional<LeaderboardSortType>?,
    ): MutableList<TeamLeaderboardEntryDTO> {
        return createMockTeamLeaderboard()
    }
}