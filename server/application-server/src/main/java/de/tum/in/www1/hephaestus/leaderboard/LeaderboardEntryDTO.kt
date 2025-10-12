package de.tum.`in`.www1.hephaestus.leaderboard

import com.fasterxml.jackson.annotation.JsonInclude
import de.tum.`in`.www1.hephaestus.gitprovider.pullrequest.PullRequestInfoDTO
import de.tum.`in`.www1.hephaestus.gitprovider.team.TeamInfoDTO
import de.tum.`in`.www1.hephaestus.gitprovider.user.UserInfoDTO

@JsonInclude(JsonInclude.Include.NON_NULL)
data class LeaderboardEntryDTO(
    val rank: Int,
    val score: Int,
    val user: UserInfoDTO? = null,
    val team: TeamInfoDTO? = null,
    val reviewedPullRequests: List<PullRequestInfoDTO>,
    val numberOfReviewedPRs: Int,
    val numberOfApprovals: Int,
    val numberOfChangeRequests: Int,
    val numberOfComments: Int,
    val numberOfUnknowns: Int,
    val numberOfCodeComments: Int,
)
