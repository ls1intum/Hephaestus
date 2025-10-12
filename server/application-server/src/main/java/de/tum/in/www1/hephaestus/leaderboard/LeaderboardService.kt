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
import kotlin.collections.ArrayDeque
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
        return when (mode) {
            LeaderboardMode.INDIVIDUAL -> createIndividualLeaderboard(after, before, if (team == "all") null else resolveTeamByPath(team), sort)
            LeaderboardMode.TEAM -> createTeamLeaderboard(after, before)
        }
    }

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
        sort: LeaderboardSortType,
        teamHierarchy: Map<Long?, List<Team>>? = null,
    ): List<LeaderboardEntryDTO> {
        logger.info(
            "Creating leaderboard dataset with timeframe: {} - {} and team: {}",
            after,
            before,
            team?.name ?: "all",
        )

        val reviews: List<PullRequestReview>
        val issueComments: List<IssueComment>

        val teamIds: Set<Long> = if (team != null) {
            val hierarchy = teamHierarchy ?: buildTeamHierarchy()
            collectTeamAndDescendantIds(team, hierarchy)
        } else {
            emptySet()
        }

        if (team != null) {
            reviews = pullRequestReviewRepository.findAllInTimeframeOfTeams(after, before, teamIds)
            issueComments = issueCommentRepository.findAllInTimeframeOfTeams(after, before, teamIds, true)
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
            if (teamIds.isNotEmpty()) {
                userRepository.findAllByTeamIds(teamIds).forEach { usersById.putIfAbsent(it.id, it) }
            }
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
    ): List<LeaderboardEntryDTO> {
        logger.info(
            "Creating team leaderboard dataset with timeframe: {} - {}",
            after,
            before,
        )

        val allTeams = teamRepository.findAll()
        val teamHierarchy = allTeams.groupBy { it.parentId }
        val targetTeams = allTeams.filter { !it.isHidden }

        if (targetTeams.isEmpty()) {
            logger.info("❌ No teams found for team leaderboard")
            return emptyList()
        }

        val teamStatsById = targetTeams.associateWith { teamEntity ->
            val entries = createIndividualLeaderboard(after, before, teamEntity, LeaderboardSortType.SCORE, teamHierarchy)
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

    private fun resolveTeamByPath(path: String?): Team? {
        if (path.isNullOrBlank()) {
            return null
        }

        val parts = path.split(" / ")
        val leaf = parts.last()
        val candidates = teamRepository.findAllByName(leaf)
        if (candidates.isEmpty()) {
            return null
        }

        val cache = mutableMapOf<Long, Team>()
        var currentByCandidate = mutableMapOf<Long, Team>()

        candidates.forEach { candidate ->
            val candidateId = candidate.id ?: return@forEach
            cache[candidateId] = candidate
            currentByCandidate[candidateId] = candidate
        }

        if (currentByCandidate.isEmpty()) {
            return null
        }

        if (parts.size == 1) {
            val sole = currentByCandidate.keys.singleOrNull() ?: return null
            return cache[sole]
        }

        for (index in parts.size - 2 downTo 0) {
            if (currentByCandidate.size <= 1) {
                break
            }

            val expected = parts[index]
            val nextVisibleByCandidate = mutableMapOf<Long, Team?>()
            var pendingResolution = true

            while (pendingResolution) {
                pendingResolution = false
                val missingIds = mutableSetOf<Long>()

                currentByCandidate.forEach { (candidateId, cursor) ->
                    if (candidateId in nextVisibleByCandidate) {
                        return@forEach
                    }

                    var parentId = cursor.parentId
                    while (parentId != null) {
                        val parent = cache[parentId]
                        if (parent == null) {
                            missingIds.add(parentId)
                            break
                        }
                        if (!parent.isHidden) {
                            nextVisibleByCandidate[candidateId] = parent
                            break
                        }
                        parentId = parent.parentId
                    }

                    if (parentId == null && candidateId !in nextVisibleByCandidate) {
                        nextVisibleByCandidate[candidateId] = null
                    }
                }

                if (missingIds.isNotEmpty()) {
                    teamRepository.findAllById(missingIds).forEach { parent ->
                        val parentId = parent.id ?: return@forEach
                        cache.putIfAbsent(parentId, parent)
                    }
                    pendingResolution = true
                }
            }

            val filtered = mutableMapOf<Long, Team>()
            currentByCandidate.forEach { (candidateId, _) ->
                val nextVisible = nextVisibleByCandidate[candidateId]
                if (nextVisible != null && nextVisible.name == expected) {
                    filtered[candidateId] = nextVisible
                    nextVisible.id?.let { cache.putIfAbsent(it, nextVisible) }
                }
            }

            currentByCandidate = filtered

            if (currentByCandidate.isEmpty()) {
                return null
            }

            if (currentByCandidate.size == 1) {
                val onlyId = currentByCandidate.keys.first()
                return cache[onlyId]
            }
        }

        if (currentByCandidate.size > 1) {
            preloadAncestors(currentByCandidate.values, cache)
            for (candidateId in currentByCandidate.keys) {
                val candidate = cache[candidateId]
                if (candidate != null && equalsVisiblePath(candidate, parts, cache)) {
                    return candidate
                }
            }
            logger.warn(
                "Ambiguous team path '{}' resolved to multiple candidates; picking first.",
                sanitizeForLog(path),
            )
        }

        val anyId = currentByCandidate.keys.firstOrNull() ?: return null
        return cache[anyId]
    }

    private fun equalsVisiblePath(team: Team, parts: List<String>, cache: Map<Long, Team>): Boolean {
        var index = parts.size - 1
        var current: Team? = team
        while (current != null) {
            if (!current.isHidden) {
                if (index < 0 || parts[index] != current.name) {
                    return false
                }
                index -= 1
            }
            val parentId = current.parentId
            current = parentId?.let(cache::get)
        }
        return index < 0
    }

    private fun preloadAncestors(teams: Collection<Team>, cache: MutableMap<Long, Team>) {
        var pending = teams
            .mapNotNull { it.parentId }
            .filterNot { cache.containsKey(it) }
            .toMutableSet()

        while (pending.isNotEmpty()) {
            val nextRound = mutableSetOf<Long>()
            teamRepository.findAllById(pending).forEach { parent ->
                val parentId = parent.id ?: return@forEach
                if (!cache.containsKey(parentId)) {
                    cache[parentId] = parent
                }
                val ancestorId = parent.parentId
                if (ancestorId != null && !cache.containsKey(ancestorId)) {
                    nextRound.add(ancestorId)
                }
            }
            pending = nextRound
        }
    }

    private fun sanitizeForLog(input: String?): String? = input?.replace(Regex("[\r\n]"), "")

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

    private fun buildTeamHierarchy(): Map<Long?, List<Team>> = teamRepository.findAll().groupBy { it.parentId }

    private fun collectTeamAndDescendantIds(team: Team, hierarchy: Map<Long?, List<Team>>): Set<Long> {
        val result = mutableSetOf<Long>()
        val queue = ArrayDeque<Team>()
        queue.add(team)

        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            val currentId = current.id ?: continue
            if (result.add(currentId)) {
                hierarchy[currentId].orEmpty().forEach { child -> queue.add(child) }
            }
        }

        return result
    }
}
