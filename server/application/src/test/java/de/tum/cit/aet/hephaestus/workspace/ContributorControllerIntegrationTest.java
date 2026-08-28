package de.tum.cit.aet.hephaestus.workspace;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.contributors.ContributorDTO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

class ContributorControllerIntegrationTest extends AbstractWorkspaceIntegrationTest {

    @Autowired
    private WebTestClient webTestClient;

    @Test
    void globalContributorsEndpointReturnsEmptyListWhenTokenMissing() {
        var response = webTestClient
            .get()
            .uri("/contributors")
            .accept(MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus()
            .isOk()
            .expectBodyList(ContributorDTO.class)
            .returnResult()
            .getResponseBody();

        assertThat(response).isNotNull();
        assertThat(response).isEmpty();
    }
}
