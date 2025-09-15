package de.tum.`in`.www1.hephaestus.teamleaderboard.kotlin

import de.tum.`in`.www1.hephaestus.gitprovider.issuecomment.IssueCommentRepository
import de.tum.`in`.www1.hephaestus.gitprovider.pullrequestreview.PullRequestReviewRepository
import de.tum.`in`.www1.hephaestus.gitprovider.team.TeamRepository
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

    @Suppress("UNREACHABLE_CODE")
    fun createTeamLeaderboard(
        after: OffsetDateTime,
        before: OffsetDateTime,
    ): MutableList<TeamLeaderboardEntryDTO> {
        logger.info(
            "Creating leaderboard dataset with timeframe: {} - {} for all teams",
            after,
            before,
        )

        TODO("fetch all pull requests reviews")
        val reviews = pullRequestReviewRepository.findAllInTimeframe(after, before)


        TODO("group PR reviews fro the same repository to the according team")

        TODO("Let the scoring service grade the PR reviews")

        TODO("Aggregate the scored result into a general PR review score for the team")

        TODO("Add this for the review comments as well")

        TODO("Order the scores for the Team Leaderboard")

        TODO("Construct the list of TeamLeaderboardEntryDTOs to send back to the frontend")


    }
}