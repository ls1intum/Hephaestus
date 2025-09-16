package de.tum.`in`.www1.hephaestus.teamleaderboard.kotlin

import de.tum.`in`.www1.hephaestus.gitprovider.issuecomment.IssueComment
import de.tum.`in`.www1.hephaestus.gitprovider.issuecomment.IssueCommentRepository
import de.tum.`in`.www1.hephaestus.gitprovider.pullrequestreview.PullRequestReview
import de.tum.`in`.www1.hephaestus.gitprovider.pullrequestreview.PullRequestReviewRepository
import de.tum.`in`.www1.hephaestus.gitprovider.team.Team
import de.tum.`in`.www1.hephaestus.gitprovider.team.TeamRepository
import de.tum.`in`.www1.hephaestus.leaderboard.LeaderboardSortType
import de.tum.`in`.www1.hephaestus.leaderboard.ScoringService
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import java.time.Instant

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

    @Autowired
    private lateinit var scoringService: ScoringService

    // TODO: private attributes that are needed for the class to fulfill it's duty
    // ---
    // TODO: add the method bodies to the according functions
    fun createTeamLeaderboard(
        after: Instant,
        before: Instant,
        team: String?,
        sort: LeaderboardSortType?,
    ): MutableList<TeamLeaderboardEntryDTO> {

        return createMockTeamLeaderboard()
    }

    @Suppress("UNREACHABLE_CODE")
    fun createTeamLeaderboard(
        after: Instant,
        before: Instant,
    ): MutableList<TeamLeaderboardEntryDTO> {
        logger.info(
            "Creating leaderboard dataset with timeframe: {} - {} for all teams",
            after,
            before,
        )

//        TODO("fetch all teams from the team repository")
        val teams: List<Team> = teamRepository.findAll()

//        TODO("fetch all pull requests reviews by their respective team")
        val reviewsByTeam: Map<Team, List<PullRequestReview>> =
            teams.associateWith { team -> pullRequestReviewRepository.findAllInTimeframeOfTeam(after, before, team.id) }

//        TODO("Repeat that for the review comments as well")
        val commentsByTeam: Map<Team, List<IssueComment>> =
            teams.associateWith { team ->
                issueCommentRepository.findAllInTimeframeOfTeam(
                    after,
                    before,
                    team.id,
                    true
                )
            }

        TODO("Let the scoring service grade the PR reviews")


        TODO("Aggregate the scored result into a general PR review score for the team")

        TODO("Add this for the review comments as well")

        TODO("Order the scores for the Team Leaderboard")

        TODO("Construct the list of TeamLeaderboardEntryDTOs to send back to the frontend")


    }
}