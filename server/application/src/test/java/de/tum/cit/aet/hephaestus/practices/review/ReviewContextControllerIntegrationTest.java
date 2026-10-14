package de.tum.cit.aet.hephaestus.practices.review;

import de.tum.cit.aet.hephaestus.practices.review.ReviewIntegrationData.Scenario;
import de.tum.cit.aet.hephaestus.testconfig.BaseIntegrationTest;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.reactive.server.WebTestClient;

@Import(ReviewIntegrationData.class)
class ReviewContextControllerIntegrationTest extends BaseIntegrationTest {
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
    void absoluteHttpsAddressIsRequired() {
        for (String url : List.of("/relative", "http://example.test/item", "https://user@example.test/item"))
            http.get()
                    .uri(b -> b.path(root() + "/practices/review-context")
                            .queryParam("url", url)
                            .build())
                    .headers(h -> h.setBearerAuth(ReviewIntegrationData.memberToken(scenario)))
                    .exchange()
                    .expectStatus()
                    .isBadRequest();
    }

    @Test
    void anUnknownConnectedPageDoesNotResolveByItsName() {
        http.get()
                .uri(b -> b.path(root() + "/practices/review-context")
                        .queryParam("url", "https://github.com/unknown/project/pull/1")
                        .build())
                .headers(h -> h.setBearerAuth(ReviewIntegrationData.memberToken(scenario)))
                .exchange()
                .expectStatus()
                .isNotFound();
    }

    @Test
    void lookupRequiresAuthentication() {
        http.get()
                .uri(b -> b.path(root() + "/practices/review-context")
                        .queryParam("url", "https://github.com/owner/project/pull/1")
                        .build())
                .exchange()
                .expectStatus()
                .isUnauthorized();
    }
}
