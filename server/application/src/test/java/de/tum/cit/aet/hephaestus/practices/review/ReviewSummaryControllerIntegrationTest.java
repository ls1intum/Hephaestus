package de.tum.cit.aet.hephaestus.practices.review;

import de.tum.cit.aet.hephaestus.practices.result.ResultMarkService;
import de.tum.cit.aet.hephaestus.practices.review.ReviewIntegrationData.Scenario;
import de.tum.cit.aet.hephaestus.practices.spi.ResultMarkKind;
import de.tum.cit.aet.hephaestus.practices.spi.ResultOutcome;
import de.tum.cit.aet.hephaestus.testconfig.BaseIntegrationTest;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.reactive.server.WebTestClient;

@Import(ReviewIntegrationData.class)
class ReviewSummaryControllerIntegrationTest extends BaseIntegrationTest {
    @Autowired
    ReviewIntegrationData data;

    @Autowired
    ResultMarkService marks;

    @Autowired
    WebTestClient http;

    Scenario scenario;

    long scenarioAccountId;

    @BeforeEach
    void setUp() {
        databaseTestUtils.cleanDatabase();
        scenario = data.scenario();
        scenarioAccountId = Objects.requireNonNull(scenario.account().getId());
    }

    String root() {
        return "/workspaces/" + scenario.workspace().getWorkspaceSlug();
    }

    WebTestClient.ResponseSpec get(String path) {
        return http.get()
                .uri(root() + path)
                .headers(h -> h.setBearerAuth(ReviewIntegrationData.memberToken(scenario)))
                .exchange();
    }

    @Test
    void summaryUsesDurableReviewIdentity() {
        var r = data.result(scenario, ResultOutcome.NOT_MET, Instant.now());
        get("/practices/reviews/" + r.getReviewId())
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.id")
                .isEqualTo(r.getReviewId().toString())
                .jsonPath("$.item.id")
                .isEqualTo(Long.toString(r.getItemId()));
    }

    @Test
    void usageHasAnExplicitEmptyAttemptListAfterRuntimeExpiry() {
        var r = data.result(scenario, ResultOutcome.MET, Instant.now());
        get("/practices/reviews/" + r.getReviewId() + "/usage")
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.attempts.length()")
                .isEqualTo(0);
    }

    @Test
    void foreignReviewIsNotFound() {
        var r = data.result(data.scenario(), ResultOutcome.MET, Instant.now());
        get("/practices/reviews/" + r.getReviewId()).expectStatus().isNotFound().expectBody(Void.class);
    }

    @Test
    void shouldRetainPinnedPracticeAndDeveloperFactsAfterRuntimeExpiry() {
        var result = data.result(scenario, ResultOutcome.NOT_MET, Instant.now());
        get("/practices/reviews")
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.content[0].id")
                .isEqualTo(result.getReviewId().toString())
                .jsonPath("$.content[0].origin")
                .isEqualTo("REQUEST")
                .jsonPath("$.content[0].requestedBy.id")
                .isEqualTo(Long.toString(scenarioAccountId))
                .jsonPath("$.content[0].practices[0].practiceVersionId")
                .isEqualTo(Long.toString(Objects.requireNonNull(
                        scenario.practice().getCurrentVersion().getId())))
                .jsonPath("$.content[0].practices[0].results.notMet")
                .isEqualTo(1)
                .jsonPath("$.content[0].jobCount")
                .isEqualTo(0);
    }

    @Test
    void shouldOrderReviewsCreatedAtTheSameInstantByTheirStableIdentity() {
        var at = Instant.parse("2026-10-13T00:00:00Z");
        var first = data.result(scenario, ResultOutcome.MET, at);
        data.end(first, at);
        var second = data.result(scenario, ResultOutcome.NOT_MET, at);
        var ordered = List.of(
                        first.getReviewId().toString(), second.getReviewId().toString())
                .stream()
                .sorted(Comparator.reverseOrder())
                .toList();
        get("/practices/reviews")
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.content[0].id")
                .isEqualTo(ordered.getFirst())
                .jsonPath("$.content[1].id")
                .isEqualTo(ordered.getLast());
    }

    @Test
    void shouldExcludeIncorrectProofFromPracticeFiltersAndRestoreItWhenTheMarkIsRemoved() {
        var result = data.result(scenario, ResultOutcome.MET, Instant.now());
        var mark = data.mark(result, ResultMarkKind.INCORRECT, scenarioAccountId);
        String path = "/practices/reviews?practiceSlug=" + scenario.practice().getSlug();
        get(path)
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.page.totalElements")
                .isEqualTo(0);
        marks.remove(
                scenario.workspace().getId(),
                result.getId(),
                mark.getId(),
                scenarioAccountId,
                "The recorded proof is correct.");
        get(path)
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.page.totalElements")
                .isEqualTo(1)
                .jsonPath("$.content[0].id")
                .isEqualTo(result.getReviewId().toString());
    }

    @Test
    void listIsScopedToWorkspace() {
        data.result(scenario, ResultOutcome.MET, Instant.now());
        data.result(data.scenario(), ResultOutcome.NOT_MET, Instant.now());
        get("/practices/reviews")
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.page.totalElements")
                .isEqualTo(1);
    }
}
