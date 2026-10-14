package de.tum.cit.aet.hephaestus.practices.review.schedule;

import de.tum.cit.aet.hephaestus.practices.review.ReviewIntegrationData;
import de.tum.cit.aet.hephaestus.practices.review.ReviewIntegrationData.Scenario;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewBatchStatus;
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
    ReviewBatchRepository batches;

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
                .jsonPath("$.totalElements")
                .isEqualTo(0);
    }

    @Test
    void unknownBatchIsNotFound() {
        get("/practices/review-batches/" + UUID.randomUUID())
                .expectStatus()
                .isNotFound()
                .expectBody(Void.class);
    }

    @Test
    void shouldReturnConflictWhenAnEndedBatchIsResumed() {
        var now = Instant.now();
        var batch = batches.saveAndFlush(ReviewBatch.builder()
                .id(UUID.randomUUID())
                .workspaceId(scenario.workspace().getId())
                .kind("scm.pull_request")
                .fromAt(now.minusSeconds(60))
                .toAt(now)
                .status(ReviewBatchStatus.CANCELLED)
                .requestedByAccountId(scenario.account().getId())
                .createdAt(now)
                .updatedAt(now)
                .finishedAt(now)
                .build());
        http.patch()
                .uri(root() + "/practices/review-batches/" + batch.getId() + "/status")
                .headers(h -> h.setBearerAuth(ReviewIntegrationData.memberToken(scenario)))
                .header("If-Match", "\"" + batch.getVersion() + "\"")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("status", "RUNNING"))
                .exchange()
                .expectStatus()
                .isEqualTo(409)
                .expectBody()
                .jsonPath("$.type")
                .isEqualTo("/problems/review-batch-conflict")
                .jsonPath("$.status")
                .isEqualTo(409);
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
                .isForbidden()
                .expectBody(Void.class);
    }
}
