package de.tum.cit.aet.hephaestus.practices.review.schedule;

import de.tum.cit.aet.hephaestus.practices.review.ReviewIntegrationData;
import de.tum.cit.aet.hephaestus.practices.review.ReviewIntegrationData.Scenario;
import de.tum.cit.aet.hephaestus.testconfig.BaseIntegrationTest;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

@Import(ReviewIntegrationData.class)
class ReviewScheduleControllerIntegrationTest extends BaseIntegrationTest {
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
    void scheduleListStartsEmpty() {
        get("/practices/review-schedules")
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.length()")
                .isEqualTo(0);
    }

    @Test
    void repositoryScheduleRejectsMissingBudget() {
        http.post()
                .uri(root() + "/practices/review-schedules")
                .headers(h -> h.setBearerAuth(ReviewIntegrationData.memberToken(scenario)))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("itemKind", "scm.repository", "cadence", "DAILY", "enabled", true))
                .exchange()
                .expectStatus()
                .isBadRequest();
    }

    @Test
    void foreignScheduleCannotBeChanged() {
        var id = UUID.randomUUID();
        http.patch()
                .uri(root() + "/practices/review-schedules/" + id)
                .headers(h -> h.setBearerAuth(ReviewIntegrationData.memberToken(scenario)))
                .header("If-Match", "\"0\"")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("enabled", false))
                .exchange()
                .expectStatus()
                .isNotFound();
    }
}
