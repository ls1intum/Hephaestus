package de.tum.`in`.www1.hephaestus.teamleaderboard.kotlin

import de.tum.`in`.www1.hephaestus.gitprovider.pullrequestreview.PullRequestReviewRepository
import de.tum.`in`.www1.hephaestus.gitprovider.team.TeamRepository
import de.tum.`in`.www1.hephaestus.testconfig.BaseIntegrationTest
import de.tum.`in`.www1.hephaestus.testconfig.PostgreSQLTestContainer
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.testcontainers.junit.jupiter.Testcontainers

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class TeamRepositoryIntegrationTest : BaseIntegrationTest() {

    @Autowired
    lateinit var teamRepository: TeamRepository

    @Autowired
    lateinit var pullRequestReviewRepository: PullRequestReviewRepository

    @Test
//    @Disabled
    fun testFindAllReturnsTeams() {
        println("=== TEAMS ===")
        println("JDBC URL: " + PostgreSQLTestContainer.getInstance().jdbcUrl)
        val teams = teamRepository.findAll()
        assertTrue(teams.isNotEmpty())
        teams.forEach { println(it) }
    }

    @Test
    fun testFindAllByTimeframeReturnsPullRequestreviews() {
        println("=== PRRs ===")
//        val prr = pullRequestReviewRepository.findAllInTimeframe(
//            OffsetDateTime.parse("1970-01-01T00:00:00Z"),
//            OffsetDateTime.now()
//        )
        println("JDBC URL: " + PostgreSQLTestContainer.getInstance().jdbcUrl)
        println(pullRequestReviewRepository)
        val prr = pullRequestReviewRepository.findAll()
        assertTrue(prr.isNotEmpty())
        if (prr.isNotEmpty()) prr.forEach { println(it) } else println(" > No PullRequestReviews found < ")
    }

}