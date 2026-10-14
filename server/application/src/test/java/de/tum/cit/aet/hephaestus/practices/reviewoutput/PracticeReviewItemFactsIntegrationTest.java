package de.tum.cit.aet.hephaestus.practices.reviewoutput;

import de.tum.cit.aet.hephaestus.practices.review.ReviewIntegrationData;
import de.tum.cit.aet.hephaestus.practices.review.ReviewIntegrationData.Scenario;
import de.tum.cit.aet.hephaestus.testconfig.BaseIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.reactive.server.WebTestClient;

@Import(ReviewIntegrationData.class)
class PracticeReviewItemFactsIntegrationTest extends BaseIntegrationTest {
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
    void itemReferenceUsesTheCanonicalId() {
        get("/practices/items/" + scenario.item().getId())
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.id")
                .isEqualTo(scenario.item().getId().toString())
                .jsonPath("$.kind")
                .isEqualTo("scm.pull_request");
    }

    @Test
    void foreignItemCannotExposeItsFacts() {
        var other = data.scenario();
        get("/practices/items/" + other.item().getId() + "/facts")
                .expectStatus()
                .isNotFound();
    }

    @Test
    void itemWithNoEventsReturnsAnEmptyPage() {
        get("/practices/items/" + scenario.item().getId() + "/events")
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.page.totalElements")
                .isEqualTo(0);
    }
}
