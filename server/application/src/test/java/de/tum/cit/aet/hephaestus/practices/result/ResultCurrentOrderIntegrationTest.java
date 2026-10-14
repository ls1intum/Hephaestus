package de.tum.cit.aet.hephaestus.practices.result;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.practices.model.Result;
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
import org.springframework.data.domain.PageRequest;
import org.springframework.test.web.reactive.server.WebTestClient;

@Import(ReviewIntegrationData.class)
class ResultCurrentOrderIntegrationTest extends BaseIntegrationTest {
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

    @Autowired
    ResultRepository results;

    @Test
    void lateCompletionCannotReplaceNewerOrderedReview() {
        var first = data.result(scenario, ResultOutcome.NOT_MET, Instant.now().minusSeconds(60));
        data.end(first, Instant.now().minusSeconds(30));
        var current = data.result(scenario, ResultOutcome.MET, Instant.now());
        assertThat(results.findCurrentClaim(
                        current.getWorkspaceId(),
                        current.getPracticeId(),
                        current.getItemId(),
                        current.getDeveloperId()))
                .get()
                .extracting(Result::getId)
                .isEqualTo(current.getId());
        var history = results.findByWorkspaceIdAndItemIdOrderByRecordedAtDescIdDesc(
                current.getWorkspaceId(), current.getItemId());
        assertThat(history).extracting(Result::getId).containsExactly(current.getId(), first.getId());
    }

    @Test
    void markedNewestClaimDoesNotResurrectEndedClaim() {
        var first = data.result(scenario, ResultOutcome.MET, Instant.now().minusSeconds(60));
        data.end(first, Instant.now().minusSeconds(30));
        var current = data.result(scenario, ResultOutcome.NOT_MET, Instant.now());
        data.mark(current, ResultMarkKind.INCORRECT, scenarioAccountId);
        assertThat(results.findCurrentClaim(
                        current.getWorkspaceId(),
                        current.getPracticeId(),
                        current.getItemId(),
                        current.getDeveloperId()))
                .get()
                .extracting(Result::getId)
                .isEqualTo(current.getId());
        assertThat(results.findCurrentForDeveloper(
                        current.getWorkspaceId(), scenarioAccountId, ResultMarkKind.INCORRECT, PageRequest.of(0, 20)))
                .isEmpty();
    }
}
