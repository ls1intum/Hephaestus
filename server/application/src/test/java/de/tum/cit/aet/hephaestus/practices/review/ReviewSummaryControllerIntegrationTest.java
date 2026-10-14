package de.tum.cit.aet.hephaestus.practices.review;

import de.tum.cit.aet.hephaestus.practices.review.ReviewIntegrationData.Scenario;
import de.tum.cit.aet.hephaestus.practices.spi.ResultOutcome;
import de.tum.cit.aet.hephaestus.testconfig.BaseIntegrationTest;
import java.time.Instant;
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
    WebTestClient http;

    Scenario scenario;

    @BeforeEach
    void setUp() {
        databaseTestUtils.cleanDatabase();
        scenario = data.scenario();
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
        get("/practices/reviews/" + r.getReviewId()).expectStatus().isNotFound();
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
