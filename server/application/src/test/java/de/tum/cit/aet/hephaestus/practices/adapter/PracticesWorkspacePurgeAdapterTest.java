package de.tum.cit.aet.hephaestus.practices.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.practices.result.ResultRepository;
import de.tum.cit.aet.hephaestus.practices.review.ReviewIntegrationData;
import de.tum.cit.aet.hephaestus.practices.review.ReviewIntegrationData.Scenario;
import de.tum.cit.aet.hephaestus.practices.review.ReviewRepository;
import de.tum.cit.aet.hephaestus.practices.spi.ResultOutcome;
import de.tum.cit.aet.hephaestus.testconfig.BaseIntegrationTest;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.transaction.annotation.Transactional;

@Import(ReviewIntegrationData.class)
class PracticesWorkspacePurgeAdapterTest extends BaseIntegrationTest {
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
    PracticesWorkspacePurgeAdapter purge;

    @Autowired
    ResultRepository results;

    @Autowired
    ReviewRepository reviews;

    @Test
    @Transactional
    void purgeDeletesFinalRowsOnlyForItsWorkspace() {
        var mine = data.result(scenario, ResultOutcome.NOT_MET, Instant.now());
        var other = data.result(data.scenario(), ResultOutcome.MET, Instant.now());
        purge.deleteWorkspaceData(mine.getWorkspaceId());
        assertThat(results.findById(mine.getId())).isEmpty();
        assertThat(results.findById(other.getId())).isPresent();
        assertThat(reviews.findById(mine.getReviewId())).isEmpty();
        assertThat(reviews.findById(other.getReviewId())).isPresent();
    }
}
