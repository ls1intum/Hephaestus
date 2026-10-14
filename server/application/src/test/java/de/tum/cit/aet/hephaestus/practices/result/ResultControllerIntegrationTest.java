package de.tum.cit.aet.hephaestus.practices.result;

import de.tum.cit.aet.hephaestus.practices.review.ReviewIntegrationData;
import de.tum.cit.aet.hephaestus.practices.review.ReviewIntegrationData.Scenario;
import de.tum.cit.aet.hephaestus.practices.spi.ResultMarkKind;
import de.tum.cit.aet.hephaestus.practices.spi.ResultOutcome;
import de.tum.cit.aet.hephaestus.testconfig.BaseIntegrationTest;
import java.time.Instant;
import java.util.Objects;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.reactive.server.WebTestClient;

@Import(ReviewIntegrationData.class)
class ResultControllerIntegrationTest extends BaseIntegrationTest {
    @Autowired
    ReviewIntegrationData data;

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
    void detailUsesDecimalItemAndVersionIds() {
        var r = data.result(scenario, ResultOutcome.MET, Instant.now());
        get("/practices/results/" + r.getId())
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.id")
                .isEqualTo(r.getId().toString())
                .jsonPath("$.reviewId")
                .isEqualTo(r.getReviewId().toString())
                .jsonPath("$.item.id")
                .isEqualTo(Long.toString(r.getItemId()))
                .jsonPath("$.practiceVersionId")
                .isEqualTo(Long.toString(Objects.requireNonNull(r.getPracticeVersionId())))
                .jsonPath("$.outcome")
                .isEqualTo("MET");
    }

    @Test
    void foreignWorkspaceResultIsNotVisible() {
        var other = data.scenario();
        var r = data.result(other, ResultOutcome.MET, Instant.now());
        get("/practices/results/" + r.getId()).expectStatus().isNotFound().expectBody(Void.class);
    }

    @Test
    void anotherDevelopersResultIsNotVisible() {
        var r = data.result(scenario, ResultOutcome.MET, Instant.now());
        var other = data.member(scenario.workspace().getId());
        http.get()
                .uri(root() + "/practices/results/" + r.getId())
                .headers(h -> h.setBearerAuth("mock-jwt-member-" + other.getId()))
                .exchange()
                .expectStatus()
                .isNotFound()
                .expectBody(Void.class);
    }

    @Test
    void currentAndEndedResultsRemainInHistory() {
        var old = data.result(scenario, ResultOutcome.MET, Instant.now().minusSeconds(60));
        data.end(old, Instant.now().minusSeconds(30));
        data.result(scenario, ResultOutcome.NOT_MET, Instant.now());
        get("/practices/results")
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.totalElements")
                .isEqualTo(2);
    }

    @Test
    void markedResultRemainsInHistory() {
        var r = data.result(scenario, ResultOutcome.NOT_MET, Instant.now());
        data.mark(r, ResultMarkKind.INCORRECT, scenarioAccountId);
        get("/practices/results/" + r.getId())
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.outcome")
                .isEqualTo("NOT_MET")
                .jsonPath("$.markedIncorrectAt")
                .isNotEmpty();
    }

    @Test
    void unauthenticatedRequestIsRejected() {
        http.get()
                .uri(root() + "/practices/results")
                .exchange()
                .expectStatus()
                .isUnauthorized()
                .expectBody(Void.class);
    }
}
