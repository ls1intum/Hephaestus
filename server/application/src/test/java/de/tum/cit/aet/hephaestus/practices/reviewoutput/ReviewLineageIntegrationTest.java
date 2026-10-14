package de.tum.cit.aet.hephaestus.practices.reviewoutput;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.tum.cit.aet.hephaestus.core.exception.EntityNotFoundException;
import de.tum.cit.aet.hephaestus.practices.review.ReviewIntegrationData;
import de.tum.cit.aet.hephaestus.practices.review.ReviewIntegrationData.Scenario;
import de.tum.cit.aet.hephaestus.practices.spi.LineageKind;
import de.tum.cit.aet.hephaestus.practices.spi.ResultOutcome;
import de.tum.cit.aet.hephaestus.testconfig.BaseIntegrationTest;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.reactive.server.WebTestClient;

@Import(ReviewIntegrationData.class)
class ReviewLineageIntegrationTest extends BaseIntegrationTest {
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
    ReviewLineageService lineage;

    @Test
    void resultLineageUsesDurableReviewAndProblemIds() {
        var r = data.result(scenario, ResultOutcome.NOT_MET, Instant.now());
        var graph = lineage.graph(
                r.getWorkspaceId(),
                new ReviewLineageFilterParams(LineageKind.RESULT, null, null, r.getId(), null, null));
        assertThat(graph.rootId()).isEqualTo("result:" + r.getId());
        assertThat(graph.nodes())
                .extracting(n -> n.kind())
                .contains(LineageKind.ITEM, LineageKind.REVIEW, LineageKind.RESULT, LineageKind.PROBLEM);
        assertThat(graph.edges())
                .allMatch(e -> graph.nodes().stream().anyMatch(n -> n.id().equals(e.from()))
                        && graph.nodes().stream().anyMatch(n -> n.id().equals(e.to())));
    }

    @Test
    void wrongWorkspaceCannotExpandResult() {
        var r = data.result(data.scenario(), ResultOutcome.MET, Instant.now());
        assertThatThrownBy(() -> lineage.graph(
                        scenario.workspace().getId(),
                        new ReviewLineageFilterParams(LineageKind.RESULT, null, null, r.getId(), null, null)))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void ambiguousAndMismatchedRootsAreRejected() {
        var r = data.result(scenario, ResultOutcome.MET, Instant.now());
        assertThatThrownBy(() -> lineage.graph(
                        r.getWorkspaceId(),
                        new ReviewLineageFilterParams(LineageKind.RESULT, r.getItemId(), null, r.getId(), null, null)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> lineage.graph(
                        r.getWorkspaceId(),
                        new ReviewLineageFilterParams(LineageKind.REVIEW, null, null, r.getId(), null, null)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
