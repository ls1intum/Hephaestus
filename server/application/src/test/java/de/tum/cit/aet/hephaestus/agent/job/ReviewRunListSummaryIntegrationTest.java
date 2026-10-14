package de.tum.cit.aet.hephaestus.agent.job;

import static de.tum.cit.aet.hephaestus.practices.spi.ResultOutcome.MET;
import static de.tum.cit.aet.hephaestus.practices.spi.ResultOutcome.NOT_MET;

import de.tum.cit.aet.hephaestus.integration.scm.domain.user.User;
import de.tum.cit.aet.hephaestus.practices.AbstractPracticeReviewIntegrationTest;
import de.tum.cit.aet.hephaestus.practices.model.Practice;
import de.tum.cit.aet.hephaestus.practices.model.ResultMark;
import de.tum.cit.aet.hephaestus.practices.spi.Severity;
import de.tum.cit.aet.hephaestus.practices.result.ResultMarkRepository;
import de.tum.cit.aet.hephaestus.testconfig.TestAuthUtils;
import de.tum.cit.aet.hephaestus.testconfig.WithAdminUser;
import de.tum.cit.aet.hephaestus.workspace.AccountType;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.reactive.server.WebTestClient;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * What {@code GET /practices/reviews} says of each run beyond its counts: who it is about, what occasioned it, what
 * it decided per practice, how many practices it measured and its place among the reviews of its work. Every
 * assertion is on rows this class wrote.
 */
class ReviewRunListSummaryIntegrationTest extends AbstractPracticeReviewIntegrationTest {

    private static final String REVIEWS_URI = "/workspaces/{slug}/practices/reviews";

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ResultMarkRepository invalidationRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Workspace workspace;
    private User developer;
    private Practice smallDiff;
    private ReviewJob firstReview;
    private ReviewJob secondReview;
    private ReviewJob queuedReview;

    @BeforeEach
    void seedReviews() {
        workspace = createWorkspace(
                "run-list-ws", "Run list WS", "run-list-org", AccountType.ORG, persistUser("run-list-owner"));
        ensureAdminMembership(workspace);
        developer = persistUser("run-list-developer");
        Practice explainChanges = persistPractice(workspace, null, "explain-changes", "Explain each change", null);
        smallDiff = persistPractice(workspace, null, "small-diff", "Keep the diff reviewable", null);

        firstReview = reviewAt(persistPullRequestReview(workspace, 12, null), NOW.minus(Duration.ofHours(3)));
        observe(explainChanges, firstReview, 12L, developer, MET, null, NOW.minus(Duration.ofHours(3)));

        secondReview = reviewAt(persistPullRequestReview(workspace, 12, null), NOW.minus(Duration.ofHours(1)));
        ObjectNode metadata = (ObjectNode) Objects.requireNonNull(secondReview.getMetadata());
        metadata.put("observation_origin", "MANUAL");
        secondReview.setOutput(
                objectMapper.valueToTree(Map.of("practiceCoverage", Map.of("eligible", 3, "evaluated", 3))));
        secondReview = agentJobRepository.save(secondReview);
        observe(explainChanges, secondReview, 12L, developer, NOT_MET, Severity.MAJOR, NOW.minus(Duration.ofHours(1)));
        observe(smallDiff, secondReview, 12L, developer, MET, null, NOW.minus(Duration.ofHours(1)));

        // Queued on other work: it has observed nobody yet, so only its metadata names whose work it reads.
        queuedReview = persistPullRequestReview(workspace, 13, null);
        ((ObjectNode) Objects.requireNonNull(queuedReview.getMetadata())).put("author_id", developer.getId());
        queuedReview = reviewAt(queuedReview, NOW);
    }

    /** The base class seeds a review without a purpose; the admin list reads practice reviews only. */
    private ReviewJob reviewAt(ReviewJob review, Instant createdAt) {

        review.setAvailableAt(createdAt);
        return agentJobRepository.save(review);
    }

    @Test
    @WithAdminUser
    @DisplayName("each run names its developer, its origin, its verdict per practice and its place on the work")
    void shouldDescribeWhatEachRunSawWhenAnAdminListsReviews() {
        webTestClient
                .get()
                .uri(REVIEWS_URI, workspace.getWorkspaceSlug())
                .headers(TestAuthUtils.withCurrentUser())
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.content.length()")
                .isEqualTo(3)
                // Newest first: the queued review, then the second and the first review of #12.
                .jsonPath("$.content[0].id")
                .isEqualTo(queuedReview.getId().toString())
                .jsonPath("$.content[0].developers[0].login")
                .isEqualTo("run-list-developer")
                .jsonPath("$.content[0].origin")
                .isEqualTo("LIVE")
                .jsonPath("$.content[0].positionOnWork")
                .isEqualTo(1)
                .jsonPath("$.content[0].practicesEvaluated")
                .doesNotExist()
                .jsonPath("$.content[0].practices.met")
                .isEqualTo(0)
                .jsonPath("$.content[1].id")
                .isEqualTo(secondReview.getId().toString())
                .jsonPath("$.content[1].origin")
                .isEqualTo("MANUAL")
                .jsonPath("$.content[1].developers.length()")
                .isEqualTo(1)
                .jsonPath("$.content[1].developers[0].id")
                .isEqualTo(developer.getId())
                .jsonPath("$.content[1].practices.notMet")
                .isEqualTo(1)
                .jsonPath("$.content[1].practices.met")
                .isEqualTo(1)
                .jsonPath("$.content[1].slippedPractices[0].practiceSlug")
                .isEqualTo("explain-changes")
                .jsonPath("$.content[1].slippedPractices.length()")
                .isEqualTo(1)
                .jsonPath("$.content[1].practicesEvaluated")
                .isEqualTo(3)
                .jsonPath("$.content[1].positionOnWork")
                .isEqualTo(2)
                .jsonPath("$.content[1].reviewsOnWork")
                .isEqualTo(2)
                .jsonPath("$.content[0].reviewsOnWork")
                .isEqualTo(1)
                .jsonPath("$.content[0].finishedAfterMerge")
                .isEqualTo(false)
                .jsonPath("$.content[2].id")
                .isEqualTo(firstReview.getId().toString())
                .jsonPath("$.content[2].positionOnWork")
                .isEqualTo(1)
                .jsonPath("$.content[2].practices.met")
                .isEqualTo(1);
    }

    @Test
    @WithAdminUser
    @DisplayName("two reviews of one work requested at the same instant take their places in the list order")
    void shouldOrderReviewsOfOneWorkRequestedAtTheSameInstantByTheListOrder() {
        Instant tied = NOW.minus(Duration.ofMinutes(30));
        ReviewJob one = reviewAt(persistPullRequestReview(workspace, 14, null), tied);
        ReviewJob two = reviewAt(persistPullRequestReview(workspace, 14, null), tied);
        // The entity does not update its creation time, so the tie is written directly.
        jdbcTemplate.update(
                "UPDATE review_job SET created_at = ? WHERE id IN (?, ?)",
                Timestamp.from(tied),
                one.getId(),
                two.getId());
        boolean oneFirst = one.getId().toString().compareTo(two.getId().toString()) < 0;
        ReviewJob newer = oneFirst ? two : one;

        ReviewJob older = oneFirst ? one : two;

        // Counted in (created at, id) order, so the tie has one answer however the page sorts it.
        list("?itemKind=scm.pull_request&artifactId=14")
                .jsonPath("$.content.length()")
                .isEqualTo(2)
                .jsonPath("$.content[?(@.id == '" + newer.getId() + "')].positionOnWork")
                .isEqualTo(List.of(2))
                .jsonPath("$.content[?(@.id == '" + older.getId() + "')].positionOnWork")
                .isEqualTo(List.of(1))
                .jsonPath("$.content[0].reviewsOnWork")
                .isEqualTo(2);
    }

    @Test
    @WithAdminUser
    @DisplayName("an observation marked incorrect names no developer and does not match a filter")
    void shouldLeaveAnObservationMarkedIncorrectOutOfTheRowAndTheFilters() {
        User other = persistUser("run-list-other");
        UUID wrong =
                observe(smallDiff, firstReview, 12L, other, NOT_MET, Severity.MINOR, NOW.minus(Duration.ofHours(3)));
        invalidationRepository.save(new ResultMark(
                practiceResultRepository.findById(wrong).orElseThrow(), 1L, "About the wrong person", NOW));

        list("").jsonPath("$.content[2].id")
                .isEqualTo(firstReview.getId().toString())
                .jsonPath("$.content[2].developers.length()")
                .isEqualTo(1)
                .jsonPath("$.content[2].developers[0].login")
                .isEqualTo("run-list-developer")
                .jsonPath("$.content[2].practices.notMet")
                .isEqualTo(0)
                .jsonPath("$.content[2].practiceResults.notMet")
                .isEqualTo(0)
                .jsonPath("$.content[2].practiceResults.met")
                .isEqualTo(1);
        list("?subjectUserId=" + other.getId()).jsonPath("$.content.length()").isEqualTo(0);
    }

    private WebTestClient.BodyContentSpec list(String query) {
        return webTestClient
                .get()
                .uri(REVIEWS_URI + query, workspace.getWorkspaceSlug())
                .headers(TestAuthUtils.withCurrentUser())
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody();
    }
}
