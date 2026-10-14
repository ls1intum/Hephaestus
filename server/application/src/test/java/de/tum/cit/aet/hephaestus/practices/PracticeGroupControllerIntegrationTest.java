package de.tum.cit.aet.hephaestus.practices;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.integration.core.spi.ItemKind;
import de.tum.cit.aet.hephaestus.practices.dto.BindPracticeGroupRequestDTO;
import de.tum.cit.aet.hephaestus.practices.dto.CreatePracticeGroupRequestDTO;
import de.tum.cit.aet.hephaestus.practices.model.PracticeGroup;
import de.tum.cit.aet.hephaestus.testconfig.TestAuthUtils;
import de.tum.cit.aet.hephaestus.testconfig.WithAdminUser;
import de.tum.cit.aet.hephaestus.workspace.AbstractWorkspaceIntegrationTest;
import de.tum.cit.aet.hephaestus.workspace.AccountType;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

class PracticeGroupControllerIntegrationTest extends AbstractWorkspaceIntegrationTest {
    @Autowired
    WebTestClient client;

    @Autowired
    PracticeGroupRepository groups;

    @Autowired
    PracticeVersionService versions;

    @Test
    @WithAdminUser
    void shouldMoveAGroupWithoutAppendingADefinitionVersion() {
        var owner = persistUser("admin");
        var workspace = createWorkspace("wire-group", "Groups", "wire-group-org", AccountType.ORG, owner);
        var practice = versions.create(
                workspace,
                "grouped-definition",
                new ItemKind("scm.pull_request"),
                DefinitionTestFixtures.definition(),
                null,
                0,
                null);
        var group = new PracticeGroup();
        group.setWorkspace(workspace);
        group.setSlug("destination");
        group.setName("Destination");
        groups.saveAndFlush(group);
        String version =
                Objects.requireNonNull(practice.getCurrentVersion().getId()).toString();
        client.put()
                .uri("/workspaces/{workspace}/practices/{slug}/group", workspace.getWorkspaceSlug(), practice.getSlug())
                .headers(TestAuthUtils.withCurrentUser())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new BindPracticeGroupRequestDTO(group.getSlug()))
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.groupSlug")
                .isEqualTo(group.getSlug())
                .jsonPath("$.currentVersion.id")
                .isEqualTo(version);
    }

    @Test
    @WithAdminUser
    void shouldStoreDeveloperVisibilityInTheInitialGroupCreation() {
        var owner = persistUser("admin");
        var workspace = createWorkspace("wire-hidden-group", "Groups", "hidden-group-org", AccountType.ORG, owner);
        client.post()
                .uri("/workspaces/{workspace}/practice-groups", workspace.getWorkspaceSlug())
                .headers(TestAuthUtils.withCurrentUser())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new CreatePracticeGroupRequestDTO(
                        "hidden-group", "Hidden group", null, null, null, null, false))
                .exchange()
                .expectStatus()
                .isCreated()
                .expectBody()
                .jsonPath("$.shownToDevelopers")
                .isEqualTo(false);
        var stored = groups.findByWorkspaceIdAndSlug(workspace.getId(), "hidden-group")
                .orElseThrow();
        assertThat(stored.isShownToDevelopers()).isFalse();
        client.get()
                .uri("/workspaces/{workspace}/practice-groups/{slug}", workspace.getWorkspaceSlug(), stored.getSlug())
                .headers(TestAuthUtils.withCurrentUser())
                .exchange()
                .expectStatus()
                .isOk()
                .expectHeader()
                .exists("ETag")
                .expectBody()
                .jsonPath("$.shownToDevelopers")
                .isEqualTo(false);
    }
}
