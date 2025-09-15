package de.tum.`in`.www1.hephaestus.teamleaderboard.kotlin

import de.tum.`in`.www1.hephaestus.gitprovider.issuecomment.IssueCommentRepository
import de.tum.`in`.www1.hephaestus.gitprovider.pullrequestreview.PullRequestReviewRepository
import de.tum.`in`.www1.hephaestus.gitprovider.team.Team
import de.tum.`in`.www1.hephaestus.gitprovider.team.TeamRepository
import de.tum.`in`.www1.hephaestus.gitprovider.user.User
import de.tum.`in`.www1.hephaestus.leaderboard.LeaderboardSortType
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import java.time.OffsetDateTime

@Service
class TeamLeaderboardService {

    companion object {
        private val logger = LoggerFactory.getLogger("TeamLeaderboardService")
    }

    @Autowired
    private lateinit var teamRepository: TeamRepository

    @Autowired
    private lateinit var pullRequestReviewRepository: PullRequestReviewRepository

    @Autowired
    private lateinit var issueCommentRepository: IssueCommentRepository

    // TODO: private attributes that are needed for the class to fulfill it's duty
    // ---
    // TODO: add the method bodies to the according functions
    fun createTeamLeaderboard(
        after: OffsetDateTime,
        before: OffsetDateTime,
        team: String?,
        sort: LeaderboardSortType?,
    ): MutableList<TeamLeaderboardEntryDTO> {

        return createMockTeamLeaderboard()
    }

    fun createTeamLeaderboard(
        after: OffsetDateTime,
        before: OffsetDateTime,
    ): MutableList<TeamLeaderboardEntryDTO> {
        logger.info(
            "Creating leaderboard dataset with timeframe: {} - {} for all teams",
            after,
            before,
        )

//        TODO("fetch all teams")
        val teams = teamRepository.findAll()


//        TODO("for each team, collect all users")
        val usersByTeam: Map<Team, Set<User>> = teams.associateWith { it.members }

        logger.debug(
            "=== usersByTeam map ===\n{}",
            usersByTeam.map { (team, users) -> "${team.name}: ${users.size} users" },
        )


        TODO("for each user, collect their reviews and comments (reuse the grouping and scoring logic)")

        TODO("aggregate user scores and stats at the team level")

        TODO("sort teams by total/average or other criteria")

        TODO("Build and return a list of team leaderboard entries")
    }
}