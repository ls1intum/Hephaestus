package de.tum.cit.aet.hephaestus.practices.reviewoutput;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.practices.result.ResultRepository;
import de.tum.cit.aet.hephaestus.practices.review.ReviewIntegrationData;
import de.tum.cit.aet.hephaestus.practices.review.ReviewIntegrationData.Scenario;
import de.tum.cit.aet.hephaestus.practices.spi.ResultOutcome;
import de.tum.cit.aet.hephaestus.testconfig.BaseIntegrationTest;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

@Import(ReviewIntegrationData.class)
class PracticeReviewOutputControllerIntegrationTest extends BaseIntegrationTest {
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

    @Autowired
    ResultRepository results;

    @Test
    void adminSeesResultAndProblemLedger() {
        var r = data.result(scenario, ResultOutcome.NOT_MET, Instant.now());
        get("/practices/reviews/results/" + r.getId())
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.reviewId")
                .isEqualTo(r.getReviewId().toString())
                .jsonPath("$.developer.accountId")
                .isEqualTo(scenario.account().getId().toString())
                .jsonPath("$.problems.length()")
                .isEqualTo(1);
        get("/practices/reviews/problems?resultId=" + r.getId())
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.page.totalElements")
                .isEqualTo(1);
    }

    @Test
    void marksRecordActorWithoutEndingTheResult() {
        var r = data.result(scenario, ResultOutcome.NOT_MET, Instant.now());
        http.post()
                .uri(root() + "/practices/reviews/results/" + r.getId() + "/marks")
                .headers(h -> h.setBearerAuth(ReviewIntegrationData.memberToken(scenario)))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("kind", "ACCEPTED", "reason", "Intentional tradeoff"))
                .exchange()
                .expectStatus()
                .isCreated()
                .expectBody()
                .jsonPath("$.kind")
                .isEqualTo("ACCEPTED")
                .jsonPath("$.removedAt")
                .isEmpty();
        assertThat(results.findById(r.getId()).orElseThrow().getEndedAt()).isNull();
        get("/practices/reviews/problems?openOnly=true")
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.page.totalElements")
                .isEqualTo(0);
    }

    @Test
    void foreignResultIsNotFound() {
        var r = data.result(data.scenario(), ResultOutcome.MET, Instant.now());
        get("/practices/reviews/results/" + r.getId()).expectStatus().isNotFound();
    }
}
