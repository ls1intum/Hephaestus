package de.tum.`in`.www1.hephaestus.leaderboard

import de.tum.`in`.www1.hephaestus.gitprovider.pullrequest.PullRequestInfoDTO
import de.tum.`in`.www1.hephaestus.gitprovider.team.Team
import de.tum.`in`.www1.hephaestus.gitprovider.team.TeamInfoDTO
import de.tum.`in`.www1.hephaestus.gitprovider.team.TeamRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.*

@Service
class TeamLeaderboardService {

    companion object {
        private val logger = LoggerFactory.getLogger("TeamLeaderboardService")
    }

    @Autowired
    private lateinit var teamRepository: TeamRepository

    @Autowired
    private lateinit var leaderboardService: LeaderboardService

    // TODO: private attributes that are needed for the class to fulfill it's duty
    // ---
    // TODO: add the method bodies to the according functions
    fun createTeamLeaderboard(
        after: Instant,
        before: Instant,
        team: String?,
        sort: LeaderboardSortType?,
    ): MutableList<TeamLeaderboardEntryDTO> {

        return createTeamLeaderboard(after, before)
    }

    fun createTeamLeaderboard(
        after: Instant,
        before: Instant,
    ): MutableList<TeamLeaderboardEntryDTO> {
        logger.info(
            "Creating leaderboard dataset with timeframe: {} - {} for all teams",
            after,
            before,
        )

        val teams: List<Team> = teamRepository.findAll()
        val teamsById = teams.associateBy { it.id }
        val leaderboardsByTeamId = teams.associate { team ->
            team.id to leaderboardService.createLeaderboard(
                after,
                before,
                Optional.of(team.name),
                Optional.of(LeaderboardSortType.SCORE)
            )
        }

        data class TeamStats(
//            val team: Team,
            val teamId: Long,
            val score: Int,
            val reviewedPrs: List<PullRequestInfoDTO>,
            val numberOfReviewedPRs: Int,
            val numberOfApprovals: Int,
            val numberOfChangeRequests: Int,
            val numberOfComments: Int,
            val numberOfUnknowns: Int,
            val numberOfCodeComments: Int,
        )

        val teamStatsById = leaderboardsByTeamId.mapValues { (teamId, entries) ->
            TeamStats(
//                team = teamsById[teamId]?: Team(),
                teamId = teamId,
                score = entries.sumOf { it.score },
                reviewedPrs = entries.flatMap { it.reviewedPullRequests() }.distinct(),
                numberOfReviewedPRs = entries.sumOf { it.numberOfReviewedPRs },
                numberOfApprovals = entries.sumOf { it.numberOfApprovals },
                numberOfChangeRequests = entries.sumOf { it.numberOfChangeRequests },
                numberOfComments = entries.sumOf { it.numberOfComments },
                numberOfUnknowns = entries.sumOf { it.numberOfUnknowns },
                numberOfCodeComments = entries.sumOf { it.numberOfCodeComments },
            )
        }

        return teamStatsById.entries
            .sortedWith(
                compareByDescending<Map.Entry<Long, TeamStats>> { it.value.score }
                    .thenBy { teamsById[it.key]?.name }
            )
            .mapIndexed { index, (teamId, teamStats) ->
                TeamLeaderboardEntryDTO(
                    rank = index + 1,
                    score = teamStats.score,
                    team = TeamInfoDTO.fromTeam(teamsById[teamId]),
                    reviewedPullRequests = teamStats.reviewedPrs,
                    numberOfReviewedPRs = teamStats.numberOfReviewedPRs,
                    numberOfApprovals = teamStats.numberOfApprovals,
                    numberOfChangeRequests = teamStats.numberOfChangeRequests,
                    numberOfComments = teamStats.numberOfComments,
                    numberOfUnknowns = teamStats.numberOfUnknowns,
                    numberOfCodeComments = teamStats.numberOfCodeComments
                )
            }
            .toMutableList()
    }
}