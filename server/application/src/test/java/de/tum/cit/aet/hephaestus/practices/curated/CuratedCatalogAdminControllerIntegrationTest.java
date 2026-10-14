package de.tum.cit.aet.hephaestus.practices.curated;

import de.tum.cit.aet.hephaestus.practices.DefinitionTestFixtures;
import de.tum.cit.aet.hephaestus.practices.dto.CatalogPracticeInputDTO;
import de.tum.cit.aet.hephaestus.testconfig.TestAuthUtils;
import de.tum.cit.aet.hephaestus.testconfig.WithAdminUser;
import de.tum.cit.aet.hephaestus.testconfig.WithMentorUser;
import de.tum.cit.aet.hephaestus.workspace.AbstractWorkspaceIntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

class CuratedCatalogAdminControllerIntegrationTest extends AbstractWorkspaceIntegrationTest {
    @Autowired
    WebTestClient client;

    @Test
    @WithAdminUser
    void shouldRequireAValidInstanceCatalogDefinition() {
        persistUser("admin");
        String slug = "instance-" + UUID.randomUUID();
        client.post()
                .uri("/admin/practice-catalog/practices")
                .headers(TestAuthUtils.withCurrentUser())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new CatalogPracticeInputDTO(
                        slug, "scm.pull_request", null, DefinitionTestFixtures.definition(), null))
                .exchange()
                .expectStatus()
                .isCreated()
                .expectBody()
                .jsonPath("$.kind")
                .isEqualTo("scm.pull_request")
                .jsonPath("$.definition.name")
                .isEqualTo(DefinitionTestFixtures.definition().name())
                .jsonPath("$.state")
                .isEqualTo("YOURS");
    }

    @Test
    @WithMentorUser
    void shouldRefuseInstanceCatalogWritesForAMember() {
        client.post()
                .uri("/admin/practice-catalog/practices")
                .headers(TestAuthUtils.withCurrentUser())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new CatalogPracticeInputDTO(
                        "unauthorized", "scm.pull_request", null, DefinitionTestFixtures.definition(), null))
                .exchange()
                .expectStatus()
                .isForbidden()
                .expectBody(Void.class);
    }
}
