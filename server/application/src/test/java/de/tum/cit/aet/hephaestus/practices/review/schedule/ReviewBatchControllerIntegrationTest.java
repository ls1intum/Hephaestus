package de.tum.cit.aet.hephaestus.practices.review.schedule;

import de.tum.cit.aet.hephaestus.practices.review.ReviewIntegrationData;
import de.tum.cit.aet.hephaestus.practices.review.ReviewIntegrationData.Scenario;
import de.tum.cit.aet.hephaestus.testconfig.BaseIntegrationTest;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

@Import(ReviewIntegrationData.class)
class ReviewBatchControllerIntegrationTest extends BaseIntegrationTest {
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
    void emptyBatchListContainsNoOtherWorkspaceRows() {
        get("/practices/review-batches")
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.page.totalElements")
                .isEqualTo(0);
    }

    @Test
    void unknownBatchIsNotFound() {
        get("/practices/review-batches/" + UUID.randomUUID()).expectStatus().isNotFound();
    }

    @Test
    void unauthenticatedBatchCreationIsRejected() {
        http.post()
                .uri(root() + "/practices/review-batches")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of(
                        "itemKind",
                        "scm.pull_request",
                        "from",
                        Instant.now().minusSeconds(60).toString(),
                        "to",
                        Instant.now().toString()))
                .exchange()
                .expectStatus()
                .isUnauthorized();
    }
}
