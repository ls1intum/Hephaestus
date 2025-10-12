package de.tum.`in`.www1.hephaestus.leaderboard

import de.tum.`in`.www1.hephaestus.gitprovider.pullrequest.PullRequest
import de.tum.`in`.www1.hephaestus.gitprovider.user.User
import java.time.Instant
import kotlin.math.max
import kotlin.math.sqrt
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class LeaguePointsCalculationServiceImpl : LeaguePointsCalculationService {

    private val logger = LoggerFactory.getLogger(LeaguePointsCalculationServiceImpl::class.java)

    override fun calculateNewPoints(user: User, entry: LeaderboardEntryDTO): Int {
        if (user.leaguePoints == 0) {
            user.leaguePoints = defaultPoints
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
            isNewPlayer(user) -> kFactorNewPlayer
            user.leaguePoints < pointsThresholdLow -> kFactorLowPoints
            user.leaguePoints < pointsThresholdHigh -> kFactorMediumPoints
            else -> kFactorHighPoints
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
            max(decayMinimum, (currentPoints * decayFactor).toInt())
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

    private companion object {
        val defaultPoints = LeaguePointsCalculationService.POINTS_DEFAULT
        val pointsThresholdHigh = LeaguePointsCalculationService.POINTS_THRESHOLD_HIGH
        val pointsThresholdLow = LeaguePointsCalculationService.POINTS_THRESHOLD_LOW
        val decayMinimum = LeaguePointsCalculationService.DECAY_MINIMUM
        val decayFactor = LeaguePointsCalculationService.DECAY_FACTOR
        val kFactorNewPlayer = LeaguePointsCalculationService.K_FACTOR_NEW_PLAYER
        val kFactorLowPoints = LeaguePointsCalculationService.K_FACTOR_LOW_POINTS
        val kFactorMediumPoints = LeaguePointsCalculationService.K_FACTOR_MEDIUM_POINTS
        val kFactorHighPoints = LeaguePointsCalculationService.K_FACTOR_HIGH_POINTS
    }
}
