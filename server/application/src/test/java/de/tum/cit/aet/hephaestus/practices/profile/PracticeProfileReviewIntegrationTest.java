package de.tum.cit.aet.hephaestus.practices.profile;

import de.tum.cit.aet.hephaestus.practices.model.Result;
import de.tum.cit.aet.hephaestus.practices.review.ReviewIntegrationData;
import de.tum.cit.aet.hephaestus.practices.review.ReviewIntegrationData.Scenario;
import de.tum.cit.aet.hephaestus.practices.spi.EvidenceAuthorization;
import de.tum.cit.aet.hephaestus.practices.spi.ResultOutcome;
import de.tum.cit.aet.hephaestus.testconfig.BaseIntegrationTest;
import java.time.Instant;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;

@Import(ReviewIntegrationData.class)
class PracticeProfileReviewIntegrationTest extends BaseIntegrationTest {
    @Autowired
    ReviewIntegrationData data;

    @Autowired
    WebTestClient http;

    @MockitoBean
    EvidenceAuthorization evidenceAuthorization;

    @BeforeEach
    void permitStoredEvidence() {
        Mockito.when(evidenceAuthorization.permitsAll(
                        ArgumentMatchers.anyLong(), ArgumentMatchers.anyCollection(), ArgumentMatchers.any()))
                .thenAnswer(i -> {
                    long ws = i.getArgument(0);
                    Collection<Result> rows = i.getArgument(1);
                    return rows.stream()
                            .filter(r -> r.getWorkspaceId() == ws)
                            .map(Result::getId)
                            .collect(Collectors.toSet());
                });
    }

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
    void profileListsDurableReviewIds() {
        var r = data.result(scenario, ResultOutcome.MET, Instant.now());
        get("/practice-profile/reviews")
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.page.totalElements")
                .isEqualTo(1)
                .jsonPath("$.content[0].review.id")
                .isEqualTo(r.getReviewId().toString());
    }

    @Test
    void foreignReviewIsNotFound() {
        var r = data.result(data.scenario(), ResultOutcome.MET, Instant.now());
        get("/practice-profile/reviews/" + r.getReviewId()).expectStatus().isNotFound();
    }

    @Test
    void kindFilterIsAppliedBeforePagination() {
        data.result(scenario, ResultOutcome.MET, Instant.now());
        get("/practice-profile/reviews?itemKind=chat.thread&size=1")
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.page.totalElements")
                .isEqualTo(0)
                .jsonPath("$.content.length()")
                .isEqualTo(0);
    }

    @Test
    void inaccessibleEvidenceDoesNotPopulateTheProfile() {
        data.result(scenario, ResultOutcome.NOT_MET, Instant.now());
        Mockito.when(evidenceAuthorization.permitsAll(
                        ArgumentMatchers.anyLong(), ArgumentMatchers.anyCollection(), ArgumentMatchers.any()))
                .thenReturn(Set.of());
        get("/practice-profile/reviews")
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.page.totalElements")
                .isEqualTo(0);
    }

    @Test
    void endedResultsStillHaveAReviewHistory() {
        var r = data.result(scenario, ResultOutcome.NOT_MET, Instant.now());
        data.end(r, Instant.now().plusSeconds(1));
        get("/practice-profile/reviews")
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.page.totalElements")
                .isEqualTo(1);
    }
}
