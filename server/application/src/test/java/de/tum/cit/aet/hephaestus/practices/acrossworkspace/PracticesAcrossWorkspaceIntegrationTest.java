package de.tum.cit.aet.hephaestus.practices.acrossworkspace;

import de.tum.cit.aet.hephaestus.practices.model.Result;
import de.tum.cit.aet.hephaestus.practices.review.ReviewIntegrationData;
import de.tum.cit.aet.hephaestus.practices.review.ReviewIntegrationData.Scenario;
import de.tum.cit.aet.hephaestus.practices.spi.EvidenceAuthorization;
import de.tum.cit.aet.hephaestus.practices.spi.ResultOutcome;
import de.tum.cit.aet.hephaestus.testconfig.BaseIntegrationTest;
import java.time.Instant;
import java.util.Collection;
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
class PracticesAcrossWorkspaceIntegrationTest extends BaseIntegrationTest {
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
    void overviewCountsEligibleAccountsWithoutPrivateDeveloperRows() {
        data.result(scenario, ResultOutcome.NOT_MET, Instant.now());
        get("/practices/workspace-overview")
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.practices.length()")
                .isEqualTo(1);
    }

    @Test
    void emptyWorkspaceHasNoResultTotalsFromAnotherTenant() {
        data.result(data.scenario(), ResultOutcome.NOT_MET, Instant.now());
        get("/practices/tiles")
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$[0].outcomes.notMet")
                .isEqualTo(0);
    }
}
