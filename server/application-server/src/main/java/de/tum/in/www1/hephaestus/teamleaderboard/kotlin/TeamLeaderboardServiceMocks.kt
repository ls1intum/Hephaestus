package de.tum.`in`.www1.hephaestus.teamleaderboard.kotlin

import de.tum.`in`.www1.hephaestus.gitprovider.label.LabelInfoDTO
import de.tum.`in`.www1.hephaestus.gitprovider.repository.RepositoryInfoDTO
import de.tum.`in`.www1.hephaestus.gitprovider.team.TeamInfoDTO
import de.tum.`in`.www1.hephaestus.gitprovider.user.UserInfoDTO


fun createMockTeamLeaderboard(): MutableList<TeamLeaderboardEntryDTO> {
    // Mock values for debug purpose

    val repoMock = RepositoryInfoDTO(
        1L,
        "webapp",
        "team/webapp",
        "Frontend repo",
        "https://github.com/team/webapp"
    )

    val labelsMock = LabelInfoDTO(
        1L,
        "bug",
        "#d73a4a",
        repoMock
    )

    val membersMock = UserInfoDTO(
        98L,
        "gandalf.the.white",
        "gandalf@ichhassesauron.au",
        "https://http.cat/305",
        "Gandalf der Weiße",
        "https://github.com/gandalf",
        19765
    )

    val teamInfoMock = TeamInfoDTO(
        1L,
        "Frontend Masters",
        "#ffcc00",
        listOf(repoMock),
        listOf(labelsMock),
        listOf(membersMock),
        false
    )

    val entryMock = TeamLeaderboardEntryDTO(
        1,
        1001,
        teamInfoMock,
        listOf(),
        100,
        12,
        85,
        1,
        6,
        42
    )

    return mutableListOf(entryMock)
}