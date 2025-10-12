package de.tum.`in`.www1.hephaestus.leaderboard

import de.tum.`in`.www1.hephaestus.gitprovider.pullrequest.PullRequest
import de.tum.`in`.www1.hephaestus.gitprovider.user.User
import java.time.Instant
import kotlin.math.max
import kotlin.math.sqrt
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class LeaguePointsCalculationService {

    private val logger = LoggerFactory.getLogger(LeaguePointsCalculationService::class.java)

    fun calculateNewPoints(user: User, entry: LeaderboardEntryDTO): Int {
        if (user.leaguePoints == 0) {
            user.leaguePoints = POINTS_DEFAULT
        }

        val oldPoints = user.leaguePoints
        val kFactor = getKFactor(user)
        val decay = calculateDecay(user.leaguePoints)
        val performanceBonus = calculatePerformanceBonus(entry.score)
        val placementBonus = calculatePlacementBonus(entry.rank)
        val pointChange = (kFactor * (performanceBonus + placementBonus - decay)).toInt()
        val newPoints = max(1, oldPoints + pointChange)

        logger.info(
            "Points calculation: old={}, k={}, decay={}, performanceBonus={}, placement={}, pointchange={}, new={}",
            oldPoints,
            kFactor,
            decay,
            performanceBonus,
            placementBonus,
            pointChange,
            newPoints,
        )

        return newPoints
    }

    private fun getKFactor(user: User): Double {
        return when {
            isNewPlayer(user) -> K_FACTOR_NEW_PLAYER
            user.leaguePoints < POINTS_THRESHOLD_LOW -> K_FACTOR_LOW_POINTS
            user.leaguePoints < POINTS_THRESHOLD_HIGH -> K_FACTOR_MEDIUM_POINTS
            else -> K_FACTOR_HIGH_POINTS
        }
    }

    private fun isNewPlayer(user: User): Boolean {
        val thirtyDaysAgo = Instant.now().minusSeconds(30L * 24 * 60 * 60)
        return user.mergedPullRequests
            .asSequence()
            .filter { it.isMerged }
            .mapNotNull(PullRequest::getMergedAt)
            .none { it.isAfter(thirtyDaysAgo) }
    }

    private fun calculateDecay(currentPoints: Int): Int {
        return if (currentPoints > 0) {
            max(DECAY_MINIMUM, (currentPoints * DECAY_FACTOR).toInt())
        } else {
            0
        }
    }

    private fun calculatePerformanceBonus(score: Int): Int {
        return (sqrt(score.toDouble()) * 10).toInt()
    }

    private fun calculatePlacementBonus(placement: Int): Int {
        return if (placement <= 3) {
            20 * (4 - placement)
        } else {
            0
        }
    }

    companion object {
        // Starting points for new players
        const val POINTS_DEFAULT: Int = 1000
        // Upper bound for first reduction in the k-factor
        const val POINTS_THRESHOLD_HIGH: Int = 1750
        // Lower bound for first reduction in the k-factor
        const val POINTS_THRESHOLD_LOW: Int = 1250
        // Minimum amount of points to decay each cycle
        const val DECAY_MINIMUM: Int = 10
        // Factor to determine how much of the current points are decayed each cycle
        const val DECAY_FACTOR: Double = 0.05
        // K-factors depending on the player's league points
        const val K_FACTOR_NEW_PLAYER: Double = 2.0
        const val K_FACTOR_LOW_POINTS: Double = 1.5
        const val K_FACTOR_MEDIUM_POINTS: Double = 1.2
        const val K_FACTOR_HIGH_POINTS: Double = 1.1
    }
}
