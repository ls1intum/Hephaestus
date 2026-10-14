package de.tum.cit.aet.hephaestus.practices.catalog.adoption;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.core.EntityTagPrecondition;
import de.tum.cit.aet.hephaestus.practices.PracticeRepository;
import de.tum.cit.aet.hephaestus.practices.catalog.CatalogService;
import de.tum.cit.aet.hephaestus.testconfig.TestAuthUtils;
import de.tum.cit.aet.hephaestus.testconfig.WithAdminUser;
import de.tum.cit.aet.hephaestus.workspace.AbstractWorkspaceIntegrationTest;
import de.tum.cit.aet.hephaestus.workspace.AccountType;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.reactive.server.WebTestClient;

class CatalogAdoptionControllerIntegrationTest extends AbstractWorkspaceIntegrationTest {
    @Autowired
    WebTestClient client;

    @Autowired
    CatalogService catalog;

    @Autowired
    PracticeRepository practices;

    @Test
    @WithAdminUser
    void shouldRequireTheCurrentPreviewBeforeAdoption() {
        var owner = persistUser("admin");
        var workspace = createWorkspace("adopt-preview", "Adopt", "adopt-org", AccountType.ORG, owner);
        var source = catalog.effectiveCatalog().installablePractices().getFirst();
        client.post()
                .uri(
                        "/workspaces/{workspace}/practice-catalog/adoption/{slug}",
                        workspace.getWorkspaceSlug(),
                        source.slug())
                .headers(TestAuthUtils.withCurrentUser())
                .exchange()
                .expectStatus()
                .isEqualTo(428)
                .expectBody(Void.class);
    }

    @Test
    @WithAdminUser
    void shouldRejectAStalePreviewWithoutChangingTheWorkspacePractice() {
        var owner = persistUser("admin");
        var workspace = createWorkspace("adopt-stale", "Adopt", "adopt-org", AccountType.ORG, owner);
        var source = catalog.effectiveCatalog().installablePractices().getFirst();
        var before = practices
                .findByWorkspaceIdAndSlug(workspace.getId(), source.slug())
                .map(practice ->
                        Objects.requireNonNull(practice.getCurrentVersion().getId()));
        client.post()
                .uri(
                        "/workspaces/{workspace}/practice-catalog/adoption/{slug}",
                        workspace.getWorkspaceSlug(),
                        source.slug())
                .headers(TestAuthUtils.withCurrentUser())
                .header("If-Match", EntityTagPrecondition.format("stale"))
                .exchange()
                .expectStatus()
                .isEqualTo(412)
                .expectBody(Void.class);
        assertThat(practices
                        .findByWorkspaceIdAndSlug(workspace.getId(), source.slug())
                        .map(practice -> Objects.requireNonNull(
                                practice.getCurrentVersion().getId())))
                .isEqualTo(before);
    }
}
