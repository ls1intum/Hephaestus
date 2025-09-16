package de.tum.`in`.www1.hephaestus.teamleaderboard.kotlin

import de.tum.`in`.www1.hephaestus.gitprovider.issue.Issue
import de.tum.`in`.www1.hephaestus.gitprovider.label.LabelInfoDTO
import de.tum.`in`.www1.hephaestus.gitprovider.pullrequest.PullRequestInfoDTO
import de.tum.`in`.www1.hephaestus.gitprovider.repository.RepositoryInfoDTO
import de.tum.`in`.www1.hephaestus.gitprovider.team.Team
import de.tum.`in`.www1.hephaestus.gitprovider.team.TeamInfoDTO
import de.tum.`in`.www1.hephaestus.gitprovider.user.UserInfoDTO
import java.time.Instant

fun createMockTeamLeaderboard(): MutableList<TeamLeaderboardEntryDTO> {
    // --- Repositories ---
    val repoOlympus = RepositoryInfoDTO(
        101L,
        "olympus-api",
        "gods/olympus-api",
        "Backend for Mount Olympus operations",
        "https://github.com/gods/olympus-api",
        emptyList()
    )
    val repoUnderworld = RepositoryInfoDTO(
        102L,
        "underworld-service",
        "gods/underworld-service",
        "Service for Underworld management",
        "https://github.com/gods/underworld-service",
        emptyList()
    )
    val repoLabyrinth = RepositoryInfoDTO(
        103L,
        "labyrinth-core",
        "gods/labyrinth-core",
        "Core logic for the Labyrinth",
        "https://github.com/gods/labyrinth-core",
        emptyList()
    )

    // --- Labels ---
    val labelThunder = LabelInfoDTO(201L, "thunder", "#1e90ff", repoOlympus)
    val labelWisdom = LabelInfoDTO(203L, "wisdom", "#228b22", repoOlympus)
    val labelCerberus = LabelInfoDTO(202L, "cerberus", "#8b0000", repoUnderworld)
    val labelMaze = LabelInfoDTO(204L, "maze", "#daa520", repoLabyrinth)

    // --- Users ---
    val memberZeus = UserInfoDTO(
        301L,
        "zeus.king",
        "zeus@olympus.gr",
        "https://http.cat/200",
        "Zeus",
        "https://github.com/zeus",
        100000
    )
    val memberHades = UserInfoDTO(
        302L,
        "hades.lord",
        "hades@underworld.gr",
        "https://http.cat/404",
        "Hades",
        "https://github.com/hades",
        90000
    )
    val memberAthena = UserInfoDTO(
        303L,
        "athena.wisdom",
        "athena@olympus.gr",
        "https://http.cat/201",
        "Athena",
        "https://github.com/athena",
        85000
    )
    val memberPoseidon = UserInfoDTO(
        304L,
        "poseidon.sea",
        "poseidon@olympus.gr",
        "https://http.cat/202",
        "Poseidon",
        "https://github.com/poseidon",
        87000
    )
    val memberMinotaur = UserInfoDTO(
        305L,
        "minotaur.labyrinth",
        "minotaur@labyrinth.gr",
        "https://http.cat/500",
        "Minotaur",
        "https://github.com/minotaur",
        60000
    )
    val memberAriadne = UserInfoDTO(
        306L,
        "ariadne.thread",
        "ariadne@labyrinth.gr",
        "https://http.cat/302",
        "Ariadne",
        "https://github.com/ariadne",
        75000
    )

    // --- Teams ---
    val teamOlympians = TeamInfoDTO(
        401L,
        "Olympians",
        null,
        "The gods of Mount Olympus",
        Team.Privacy.CLOSED,
        "Olympus Inc.",
        "https://github.com/gods/olympians",
        false,
        listOf(repoOlympus),
        listOf(labelThunder, labelWisdom),
        listOf(memberZeus, memberAthena, memberPoseidon),
        3,
        1
    )
    val teamUnderworld = TeamInfoDTO(
        402L,
        "Underworld Lords",
        null,
        "Rulers of the Underworld",
        Team.Privacy.CLOSED,
        "Underworld Ltd.",
        "https://github.com/gods/underworld-lords",
        false,
        listOf(repoUnderworld),
        listOf(labelCerberus),
        listOf(memberHades),
        1,
        1
    )
    val teamLabyrinth = TeamInfoDTO(
        403L,
        "Labyrinth Keepers",
        null,
        "Guardians of the Labyrinth",
        Team.Privacy.SECRET,
        "Labyrinth Org.",
        "https://github.com/gods/labyrinth-keepers",
        false,
        listOf(repoLabyrinth),
        listOf(labelMaze),
        listOf(memberMinotaur, memberAriadne),
        2,
        1
    )

    // --- Pull Requests ---
    val now = Instant.now()
    val prOlympus1 = PullRequestInfoDTO(
        1001L,
        1,
        "Add thunderbolt feature",
        Issue.State.OPEN,
        false,
        false,
        5,
        memberZeus,
        listOf(labelThunder),
        listOf(memberAthena),
        repoOlympus,
        100,
        10,
        null,
        null,
        "https://github.com/gods/olympus-api/pull/1",
        now,
        now
    )
    val prOlympus2 = PullRequestInfoDTO(
        1002L,
        2,
        "Refactor wisdom logic",
        Issue.State.CLOSED,
        false,
        true,
        8,
        memberAthena,
        listOf(labelWisdom),
        listOf(memberZeus),
        repoOlympus,
        200,
        20,
        now,
        now,
        "https://github.com/gods/olympus-api/pull/2",
        now,
        now
    )
    val prUnderworld1 = PullRequestInfoDTO(
        2001L,
        1,
        "Cerberus guard implementation",
        Issue.State.CLOSED,
        false,
        true,
        3,
        memberHades,
        listOf(labelCerberus),
        emptyList(),
        repoUnderworld,
        50,
        5,
        now,
        now,
        "https://github.com/gods/underworld-service/pull/1",
        now,
        now
    )
    val prLabyrinth1 = PullRequestInfoDTO(
        3001L,
        1,
        "Optimize maze algorithm",
        Issue.State.OPEN,
        true,
        false,
        2,
        memberAriadne,
        listOf(labelMaze),
        listOf(memberMinotaur),
        repoLabyrinth,
        80,
        8,
        null,
        null,
        "https://github.com/gods/labyrinth-core/pull/1",
        now,
        now
    )
    val prLabyrinth2 = PullRequestInfoDTO(
        3002L,
        2,
        "Minotaur bugfix",
        Issue.State.CLOSED,
        false,
        true,
        4,
        memberMinotaur,
        listOf(labelMaze),
        listOf(memberAriadne),
        repoLabyrinth,
        30,
        3,
        now,
        now,
        "https://github.com/gods/labyrinth-core/pull/2",
        now,
        now
    )

    // --- Leaderboard Entries ---
    val entryOlympians = TeamLeaderboardEntryDTO(
        1,
        2001,
        teamOlympians,
        listOf(prOlympus1, prOlympus2),
        2,
        12,
        3,
        15,
        1,
        8
    )
    val entryUnderworld = TeamLeaderboardEntryDTO(
        2,
        2002,
        teamUnderworld,
        listOf(prUnderworld1),
        1,
        5,
        1,
        7,
        0,
        3
    )

    val entryLabyrinth = TeamLeaderboardEntryDTO(
        3,
        2003,
        teamLabyrinth,
        listOf(prLabyrinth1, prLabyrinth2),
        2,
        8,
        2,
        10,
        2,
        5
    )

    return mutableListOf(entryOlympians, entryUnderworld, entryLabyrinth)
}