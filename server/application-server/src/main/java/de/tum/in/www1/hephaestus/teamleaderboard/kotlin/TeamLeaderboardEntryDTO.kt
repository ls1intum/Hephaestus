package de.tum.`in`.www1.hephaestus.teamleaderboard.kotlin

import de.tum.`in`.www1.hephaestus.gitprovider.pullrequest.PullRequestInfoDTO
import de.tum.`in`.www1.hephaestus.gitprovider.team.TeamInfoDTO

data class TeamLeaderboardEntryDTO(
    val rank: Int,
    val score: Int,
    val team: TeamInfoDTO,
    val reviewedPullRequests: List<PullRequestInfoDTO>,
    val numberOfReviewedPRs: Int,
    val numberOfApprovals: Int,
    val numberOfChangeRequests: Int,
    val numberOfComments: Int,
    val numberOfUnknowns: Int,
    val numberOfCodeComments: Int,
)