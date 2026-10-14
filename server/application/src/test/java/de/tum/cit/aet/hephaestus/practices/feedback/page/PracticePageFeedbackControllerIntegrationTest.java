package de.tum.cit.aet.hephaestus.practices.feedback.page;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.practices.feedback.AbstractFeedbackIntegrationTest;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackDestination;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackState;
import de.tum.cit.aet.hephaestus.testconfig.TestAuthUtils;
import de.tum.cit.aet.hephaestus.testconfig.WithAdminUser;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.reactive.server.WebTestClient;

class PracticePageFeedbackControllerIntegrationTest extends AbstractFeedbackIntegrationTest {

    @Autowired
    WebTestClient client;

    String route() {
        return "/workspaces/" + workspace.getWorkspaceSlug() + "/practices/feedback/practice-page";
    }

    @Test
    @WithAdminUser
    void shouldReturnAnEmptyPageWithoutInventingEvidenceOrReceipts() {
        var parent = piece(FeedbackDestination.PRACTICE_PAGE, FeedbackState.READY, null);
        client.get()
                .uri(route())
                .headers(TestAuthUtils.withCurrentUser())
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .json("[]");
        assertThat(feedback.findById(parent.getId()).orElseThrow().getDeliveredAt())
                .isNull();
        assertThat(copies.findByWorkspaceIdAndFeedbackIdOrderByPositionAsc(workspace.getId(), parent.getId()))
                .isEmpty();
    }

    @Test
    void shouldRequireAuthenticationForThePracticePage() {
        client.get().uri(route()).exchange().expectStatus().isUnauthorized().expectBody(Void.class);
    }
}
