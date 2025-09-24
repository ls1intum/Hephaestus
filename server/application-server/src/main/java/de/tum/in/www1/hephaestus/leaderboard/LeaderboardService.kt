package de.tum.`in`.www1.hephaestus.leaderboard

import de.tum.`in`.www1.hephaestus.gitprovider.issuecomment.IssueComment
import de.tum.`in`.www1.hephaestus.gitprovider.issuecomment.IssueCommentRepository
import de.tum.`in`.www1.hephaestus.gitprovider.pullrequest.PullRequest
import de.tum.`in`.www1.hephaestus.gitprovider.pullrequest.PullRequestInfoDTO
import de.tum.`in`.www1.hephaestus.gitprovider.pullrequestreview.PullRequestReview
import de.tum.`in`.www1.hephaestus.gitprovider.pullrequestreview.PullRequestReviewRepository
import de.tum.`in`.www1.hephaestus.gitprovider.team.Team
import de.tum.`in`.www1.hephaestus.gitprovider.team.TeamInfoDTO
import de.tum.`in`.www1.hephaestus.gitprovider.team.TeamRepository
import de.tum.`in`.www1.hephaestus.gitprovider.user.User
import de.tum.`in`.www1.hephaestus.gitprovider.user.UserInfoDTO
import de.tum.`in`.www1.hephaestus.gitprovider.user.UserRepository
import jakarta.transaction.Transactional
import java.time.Instant
import java.util.Optional
import kotlin.math.ceil
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class LeaderboardService(
    private val userRepository: UserRepository,
    private val pullRequestReviewRepository: PullRequestReviewRepository,
    private val issueCommentRepository: IssueCommentRepository,
    private val scoringService: ScoringService,
    private val teamRepository: TeamRepository,
    private val leaguePointsCalculationService: LeaguePointsCalculationService,
) {

    private val logger = LoggerFactory.getLogger("LeaderboardService")

    @Transactional
    fun createLeaderboard(
        after: Instant,
        before: Instant,
        team: String,
        sort: LeaderboardSortType,
        mode: LeaderboardMode,
    ): List<LeaderboardEntryDTO> {
//        val resolvedMode = mode ?: LeaderboardMode.INDIVIDUAL
//        val resolvedSort = sort ?: LeaderboardSortType.SCORE
        val resolvedTeam = resolveTeamByName(team)
        logger.info("\n➡️ team parameter: {}\n➡️ resolved Team: {}", team, resolvedTeam);
        return when (mode) {
            LeaderboardMode.INDIVIDUAL -> createIndividualLeaderboard(after, before, if (team == "all") null else resolveTeamByName(team), sort)
            LeaderboardMode.TEAM -> createTeamLeaderboard(after, before, team, sort)
        }
    }

//    fun createLeaderboard(
//        after: Instant,
//        before: Instant,
//        team: Optional<String>,
//        sort: Optional<LeaderboardSortType>,
//    ): List<LeaderboardEntryDTO> = createLeaderboard(
//        after,
//        before,
//        team.orElse(null),
//        sort.orElse(null),
//        null,
//    )

    fun createLeaderboard(
        after: Instant,
        before: Instant,
        team: Optional<String>,
        sort: Optional<LeaderboardSortType>,
        mode: Optional<LeaderboardMode>,
    ): List<LeaderboardEntryDTO> = createLeaderboard(
        after,
        before,
        team.orElse(null),
        sort.orElse(null),
        mode.orElse(null),
    )

    private fun createIndividualLeaderboard(
        after: Instant,
        before: Instant,
        team: Team?,
//        teamName: String?,
        sort: LeaderboardSortType,
    ): List<LeaderboardEntryDTO> {
//        val resolvedTeam = team ?: resolveTeamByName(teamName)
//        val resolvedTeam = team
        logger.info(
            "Creating leaderboard dataset with timeframe: {} - {} and team: {}",
            after,
            before,
            team?.name ?: "all",
        )

        val reviews: List<PullRequestReview>
        val issueComments: List<IssueComment>

        if (team != null) {
            reviews = pullRequestReviewRepository.findAllInTimeframeOfTeam(after, before, team.id)
            issueComments = issueCommentRepository.findAllInTimeframeOfTeam(after, before, team.id, true)
        } else {
            reviews = pullRequestReviewRepository.findAllInTimeframe(after, before)
            issueComments = issueCommentRepository.findAllInTimeframe(after, before, true)
        }

        val usersById: MutableMap<Long, User> = reviews
            .mapNotNull { it.author }
            .associateBy { it.id }
            .toMutableMap()

        issueComments.mapNotNull { it.author }.forEach { usersById.putIfAbsent(it.id, it) }

        if (team != null) {
            userRepository.findAllByTeamId(team.id).forEach { usersById.putIfAbsent(it.id, it) }
        } else {
            userRepository.findAllHumanInTeams().forEach { usersById.putIfAbsent(it.id, it) }
        }

        val reviewsByUserId: Map<Long, List<PullRequestReview>> = reviews
            .filter { it.author != null }
            .groupBy { it.author.id }
        val issueCommentsByUserId: Map<Long, List<IssueComment>> = issueComments
            .filter { it.author != null }
            .groupBy { it.author.id }

        val scoresByUserId: MutableMap<Long, Int> = reviewsByUserId
            .mapValues { (userId, userReviews) ->
                calculateTotalScore(userReviews, issueCommentsByUserId[userId].orEmpty())
            }
            .toMutableMap()

        usersById.keys.forEach { userId -> scoresByUserId.putIfAbsent(userId, 0) }

        val ranking: List<Long> = scoresByUserId
            .entries
            .sortedWith(comparatorFor(sort, usersById, reviewsByUserId, issueCommentsByUserId))
            .map { it.key }

        return ranking.mapIndexed { index, userId ->
            val score = scoresByUserId[userId] ?: 0
            val user = usersById[userId]?.let(UserInfoDTO::fromUser)
            val userReviews = reviewsByUserId[userId].orEmpty()
            val userIssueComments = issueCommentsByUserId[userId].orEmpty()

            val reviewedPullRequests = userReviews
                .mapNotNull { it.pullRequest }
                .filter { it.author?.id != userId }
                .associateBy(PullRequest::getId)
                .values
                .map(PullRequestInfoDTO::fromPullRequest)

            val numberOfReviewedPRs = userReviews.mapNotNull { it.pullRequest?.id }.toSet().size
            val numberOfApprovals = userReviews.count { it.state == PullRequestReview.State.APPROVED }
            val numberOfChangeRequests = userReviews.count { it.state == PullRequestReview.State.CHANGES_REQUESTED }
            val numberOfComments = userReviews.count { it.state == PullRequestReview.State.COMMENTED && it.body != null } + userIssueComments.size
            val numberOfUnknowns = userReviews.count { it.state == PullRequestReview.State.UNKNOWN && it.body != null }
            val numberOfCodeComments = userReviews.sumOf { it.comments.size }

            LeaderboardEntryDTO(
                rank = index + 1,
                score = score,
                user = user,
                team = null,
                reviewedPullRequests = reviewedPullRequests,
                numberOfReviewedPRs = numberOfReviewedPRs,
                numberOfApprovals = numberOfApprovals,
                numberOfChangeRequests = numberOfChangeRequests,
                numberOfComments = numberOfComments,
                numberOfUnknowns = numberOfUnknowns,
                numberOfCodeComments = numberOfCodeComments,
            )
        }
    }

    private fun createTeamLeaderboard(
        after: Instant,
        before: Instant,
        team: String?,
        sort: LeaderboardSortType,
    ): List<LeaderboardEntryDTO> {
        val allTeams = teamRepository.findAll()
        val targetTeams = team?.let { name -> allTeams.filter { it.name == name } } ?: allTeams

        if (team != null && targetTeams.isEmpty()) {
            logger.info("No teams found for provided filter: {}", team)
            return emptyList()
        }

        val teamStatsById = targetTeams.associateWith { teamEntity ->
            val entries = createIndividualLeaderboard(after, before, teamEntity, sort)
            aggregateTeamStats(entries)
        }

        return teamStatsById
            .entries
            .sortedWith(
                compareByDescending<Map.Entry<Team, TeamStats>> { it.value.score }
                    .thenBy { it.key.name }
            )
            .mapIndexed { index, (teamEntity, stats) ->
                LeaderboardEntryDTO(
                    rank = index + 1,
                    score = stats.score,
                    user = null,
                    team = TeamInfoDTO.fromTeam(teamEntity),
                    reviewedPullRequests = stats.reviewedPullRequests,
                    numberOfReviewedPRs = stats.numberOfReviewedPRs,
                    numberOfApprovals = stats.numberOfApprovals,
                    numberOfChangeRequests = stats.numberOfChangeRequests,
                    numberOfComments = stats.numberOfComments,
                    numberOfUnknowns = stats.numberOfUnknowns,
                    numberOfCodeComments = stats.numberOfCodeComments,
                )
            }
    }

    private fun comparatorFor(
        sortType: LeaderboardSortType,
        usersById: Map<Long, User>,
        reviewsByUserId: Map<Long, List<PullRequestReview>>,
        issueCommentsByUserId: Map<Long, List<IssueComment>>,
    ): Comparator<Map.Entry<Long, Int>> = when (sortType) {
        LeaderboardSortType.LEAGUE_POINTS -> compareByLeaguePoints(usersById, reviewsByUserId, issueCommentsByUserId)
        LeaderboardSortType.SCORE -> compareByScore(reviewsByUserId, issueCommentsByUserId)
    }

    private fun compareByLeaguePoints(
        usersById: Map<Long, User>,
        reviewsByUserId: Map<Long, List<PullRequestReview>>,
        issueCommentsByUserId: Map<Long, List<IssueComment>>,
    ): Comparator<Map.Entry<Long, Int>> = Comparator { e1, e2 ->
        val e1LeaguePoints = usersById[e1.key]?.leaguePoints ?: 0
        val e2LeaguePoints = usersById[e2.key]?.leaguePoints ?: 0
        val leagueCompare = e2LeaguePoints.compareTo(e1LeaguePoints)
        if (leagueCompare != 0) {
            leagueCompare
        } else {
            compareByScore(reviewsByUserId, issueCommentsByUserId).compare(e1, e2)
        }
    }

    private fun compareByScore(
        reviewsByUserId: Map<Long, List<PullRequestReview>>,
        issueCommentsByUserId: Map<Long, List<IssueComment>>,
    ): Comparator<Map.Entry<Long, Int>> = Comparator { e1, e2 ->
        val scoreCompare = e2.value.compareTo(e1.value)
        if (scoreCompare != 0) {
            scoreCompare
        } else {
            val e1ReviewComments = reviewsByUserId[e1.key].orEmpty().sumOf { it.comments.size }
            val e2ReviewComments = reviewsByUserId[e2.key].orEmpty().sumOf { it.comments.size }
            val e1IssueComments = issueCommentsByUserId[e1.key].orEmpty().size
            val e2IssueComments = issueCommentsByUserId[e2.key].orEmpty().size
            val e1TotalComments = e1ReviewComments + e1IssueComments
            val e2TotalComments = e2ReviewComments + e2IssueComments
            e2TotalComments.compareTo(e1TotalComments)
        }
    }

    private fun calculateTotalScore(
        reviews: List<PullRequestReview>,
        issueComments: List<IssueComment>,
    ): Int {
        val numberOfIssueComments = issueComments
            .filter { issueComment ->
                val issueAuthorId = issueComment.issue?.author?.id
                val commentAuthorId = issueComment.author?.id
                issueAuthorId != null && commentAuthorId != null && issueAuthorId != commentAuthorId
            }
            .size

        val reviewsByPullRequest = reviews.groupBy { it.pullRequest.id }

        val totalScore = reviewsByPullRequest
            .values
            .sumOf { pullRequestReviews ->
                scoringService.calculateReviewScore(pullRequestReviews, numberOfIssueComments)
            }

        return ceil(totalScore).toInt()
    }

    @Transactional
    fun getUserLeagueStats(login: String, entry: LeaderboardEntryDTO): LeagueChangeDTO {
        val user = userRepository.findByLogin(login).orElseThrow { IllegalArgumentException("User not found with login: $login") }
        val currentLeaguePoints = user.leaguePoints
        val projectedNewPoints = leaguePointsCalculationService.calculateNewPoints(user, entry)
        return LeagueChangeDTO(user.login, projectedNewPoints - currentLeaguePoints)
    }

//    private fun resolveTeamByName(teamName: String): Team = teamName.let { name ->
//        teamRepository.findAll().firstOrNull { it.name == name }
//    }

    private fun resolveTeamByName(teamName: String): Team? = teamRepository.findFirstByName(teamName)

    private data class TeamStats(
        val score: Int,
        val reviewedPullRequests: List<PullRequestInfoDTO>,
        val numberOfReviewedPRs: Int,
        val numberOfApprovals: Int,
        val numberOfChangeRequests: Int,
        val numberOfComments: Int,
        val numberOfUnknowns: Int,
        val numberOfCodeComments: Int,
    )

    private fun aggregateTeamStats(entries: List<LeaderboardEntryDTO>): TeamStats {
        val reviewedPullRequests = entries
            .flatMap { it.reviewedPullRequests }
            .associateBy { it.id }
            .values
            .toList()

        return TeamStats(
            score = entries.sumOf { it.score },
            reviewedPullRequests = reviewedPullRequests,
            numberOfReviewedPRs = entries.sumOf { it.numberOfReviewedPRs },
            numberOfApprovals = entries.sumOf { it.numberOfApprovals },
            numberOfChangeRequests = entries.sumOf { it.numberOfChangeRequests },
            numberOfComments = entries.sumOf { it.numberOfComments },
            numberOfUnknowns = entries.sumOf { it.numberOfUnknowns },
            numberOfCodeComments = entries.sumOf { it.numberOfCodeComments },
        )
    }
}
